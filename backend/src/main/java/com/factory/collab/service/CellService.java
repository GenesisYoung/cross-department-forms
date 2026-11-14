package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.messaging.EventPublisher;
import com.factory.collab.messaging.SheetEvent;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Applies a batch of cell edits atomically: checks cell-level permissions, upserts cells, writes the
 * audit trail and publishes one {@link SheetEvent} (live update + notifications) after commit.
 */
@Service
@RequiredArgsConstructor
public class CellService {
    private final SheetService sheetService;
    private final SheetRepository sheetRepository;
    private final SheetColumnRepository columnRepository;
    private final CellRepository cellRepository;
    private final CellChangeRepository changeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PermissionService permissions;
    private final EventPublisher events;

    @Value("${collab.sheet.max-rows}")
    private int maxRows;
    @Value("${collab.sheet.max-cols}")
    private int maxCols;

    @Transactional
    public UpdateCellsResponse update(Long sheetId, UpdateCellsRequest req, AuthUser user) {
        Sheet sheet = sheetService.lock(sheetId);
        AccessLevel level = permissions.require(user, sheet, AccessLevel.VIEW);

        Map<Integer, SheetColumn> columns = new HashMap<>();
        columnRepository.findBySheetIdOrderByColIndex(sheetId).forEach(c -> columns.put(c.getColIndex(), c));

        // 1. validate everything first: the batch is all-or-nothing
        int needRows = sheet.getRowCount(), needCols = sheet.getColCount();
        for (CellEdit e : req.edits()) {
            if (e.r() >= maxRows || e.c() >= maxCols) throw ApiException.badRequest("Cell " + CellRefs.ref(e.r(), e.c()) + " is beyond the sheet limit");
            String reason = permissions.denyReason(level, user.departmentCode(), sheet, columns.get(e.c()), e.r());
            if (reason != null) {
                throw ApiException.forbidden("You cannot edit " + CellRefs.ref(e.r(), e.c()) + ": " + reason);
            }
            needRows = Math.max(needRows, e.r() + 1);
            needCols = Math.max(needCols, e.c() + 1);
        }
        if (needCols > sheet.getColCount() && level != AccessLevel.OWNER) {
            throw ApiException.forbidden("Only the sheet owner can add columns");
        }
        if (needRows > sheet.getRowCount() && !level.atLeast(AccessLevel.EDIT)) {
            throw ApiException.forbidden("Your department cannot add rows to this sheet");
        }
        sheet.setRowCount(needRows);
        sheet.setColCount(needCols);

        // 2. apply
        Map<Long, Cell> cells = new HashMap<>();
        for (Cell c : cellRepository.findBySheetId(sheetId)) cells.put(SheetLabels.key(c.getRowIdx(), c.getColIdx()), c);

        Instant now = Instant.now();
        record Applied(CellEdit edit, String oldValue, boolean valueChanged) {}
        List<Applied> applied = new ArrayList<>();
        List<Cell> toSave = new ArrayList<>();
        List<Cell> toDelete = new ArrayList<>();
        for (CellEdit e : req.edits()) {
            long k = SheetLabels.key(e.r(), e.c());
            Cell cell = cells.get(k);
            String oldV = cell == null ? null : cell.getRawValue();
            String oldS = cell == null ? null : cell.getStyle();
            String newV = SheetService.emptyToNull(e.v());
            String newS = SheetService.emptyToNull(e.s());
            boolean valueChanged = !Objects.equals(oldV, newV);
            if (!valueChanged && Objects.equals(oldS, newS)) continue;

            if (cell == null) {
                cell = new Cell(sheetId, e.r(), e.c());
                cells.put(k, cell);
            }
            cell.setRawValue(newV);
            cell.setStyle(newS);
            cell.setUpdatedBy(user.id());
            cell.setUpdatedAt(now);
            toDelete.remove(cell);
            toSave.remove(cell);
            if (cell.isBlank()) {
                cells.remove(k);
                if (cell.getId() != null) toDelete.add(cell);
            } else {
                toSave.add(cell);
            }
            applied.add(new Applied(new CellEdit(e.r(), e.c(), newV, newS), oldV, valueChanged));
        }
        if (applied.isEmpty()) return new UpdateCellsResponse(sheet.getVersion(), 0);

        cellRepository.deleteAll(toDelete);
        cellRepository.saveAll(toSave);

        // 3. audit trail + event (labels computed against the post-edit state)
        String batchId = UUID.randomUUID().toString();
        User actor = userRepository.getReferenceById(user.id());
        Department dept = departmentRepository.getReferenceById(user.departmentId());
        List<CellChange> log = new ArrayList<>();
        List<SheetEvent.CellChangePayload> payload = new ArrayList<>();
        for (Applied a : applied) {
            CellEdit e = a.edit();
            SheetColumn meta = columns.get(e.c());
            boolean watched = meta != null && meta.isWatched() && e.r() >= sheet.getHeaderRows();
            String colLabel = SheetLabels.column(sheet, columns, cells, e.c());
            String rowLabel = SheetLabels.row(sheet, cells, e.r());
            if (a.valueChanged()) {
                CellChange ch = new CellChange();
                ch.setSheetId(sheetId);
                ch.setBatchId(batchId);
                ch.setRowIdx(e.r());
                ch.setColIdx(e.c());
                ch.setColumnLabel(colLabel);
                ch.setRowLabel(rowLabel);
                ch.setOldValue(a.oldValue());
                ch.setNewValue(e.v());
                ch.setWatched(watched);
                ch.setUser(actor);
                ch.setDepartment(dept);
                ch.setChangedAt(now);
                log.add(ch);
            }
            payload.add(new SheetEvent.CellChangePayload(e.r(), e.c(), e.v(), e.s(), a.oldValue(), a.valueChanged(),
                    watched && a.valueChanged(), colLabel, rowLabel, CellRefs.ref(e.r(), e.c())));
        }
        changeRepository.saveAll(log);
        sheet.touch();
        sheetRepository.save(sheet);

        events.publish(new SheetEvent(SheetEvent.Type.CELLS_UPDATED, sheetId, sheet.getName(), sheet.getVersion(), batchId,
                req.clientId(), user.id(), user.displayName(), user.departmentCode(), user.departmentName(),
                payload, null, null, now));
        return new UpdateCellsResponse(sheet.getVersion(), applied.size());
    }
}
