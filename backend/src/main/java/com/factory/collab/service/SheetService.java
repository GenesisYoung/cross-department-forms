package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.dto.TemplateDefinition;
import com.factory.collab.messaging.EventPublisher;
import com.factory.collab.messaging.SheetEvent;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SheetService {
    private final SheetRepository sheetRepository;
    private final SheetColumnRepository columnRepository;
    private final SheetPermissionRepository permissionRepository;
    private final SheetSubscriptionRepository subscriptionRepository;
    private final CellRepository cellRepository;
    private final CellChangeRepository changeRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final SheetTemplateRepository templateRepository;
    private final PermissionService permissions;
    private final SheetViews views;
    private final TemplateCodec templateCodec;
    private final EventPublisher events;

    @Value("${collab.sheet.max-rows}")
    private int maxRows;
    @Value("${collab.sheet.max-cols}")
    private int maxCols;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<SheetSummary> list(AuthUser user) {
        List<Sheet> sheets = sheetRepository.findAllByOrderByUpdatedAtDesc();
        Map<Long, AccessLevel> levels = permissions.levelsFor(user, sheets);
        return sheets.stream()
                .filter(s -> levels.get(s.getId()) != AccessLevel.NONE)
                .map(s -> views.summary(s, levels.get(s.getId()), user))
                .toList();
    }

    @Transactional(readOnly = true)
    public SheetDetail get(Long id, AuthUser user) {
        Sheet sheet = load(id);
        AccessLevel level = permissions.require(user, sheet, AccessLevel.VIEW);
        return views.detail(sheet, level, user);
    }

    @Transactional(readOnly = true)
    public List<ChangeDto> history(Long id, int limit, AuthUser user) {
        Sheet sheet = load(id);
        permissions.require(user, sheet, AccessLevel.VIEW);
        List<CellChange> changes = changeRepository.findBySheetIdOrderByChangedAtDescIdDesc(id, PageRequest.of(0, Math.min(limit, 500)));
        return views.changes(changes, Map.of(id, sheet.getName()), true);
    }

    @Transactional(readOnly = true)
    public List<ChangeDto> cellHistory(Long id, int row, int col, AuthUser user) {
        Sheet sheet = load(id);
        permissions.require(user, sheet, AccessLevel.VIEW);
        List<CellChange> changes = changeRepository.findBySheetIdAndRowIdxAndColIdxOrderByChangedAtDescIdDesc(id, row, col);
        return views.changes(changes, Map.of(id, sheet.getName()), true);
    }

    // ------------------------------------------------------------------ lifecycle

    @Transactional
    public SheetDetail create(CreateSheetRequest req, AuthUser user) {
        requireCreator(user);
        Department dept = resolveOwnerDepartment(req.departmentId(), user);
        TemplateDefinition def;
        Long templateId = null;
        if (req.templateId() != null) {
            SheetTemplate t = templateRepository.findById(req.templateId()).orElseThrow(() -> ApiException.notFound("Template"));
            def = templateCodec.decode(t.getDefinition());
            templateId = t.getId();
        } else {
            int rows = clamp(req.rows() == null ? 100 : req.rows(), 1, maxRows);
            int cols = clamp(req.cols() == null ? 12 : req.cols(), 1, maxCols);
            def = new TemplateDefinition(rows, cols, 1, List.of(), List.of());
        }
        Sheet sheet = createFromDefinition(req.name(), req.description(), dept, templateId, def, user);
        AccessLevel level = permissions.levelFor(user, sheet);
        publish(sheet, SheetEvent.Type.SHEET_CREATED, user, null);
        return views.detail(sheet, level, user);
    }

    /** Creates a sheet with columns and cells from a definition (templates and imports). */
    @Transactional
    public Sheet createFromDefinition(String name, String description, Department dept, Long templateId,
                                      TemplateDefinition def, AuthUser user) {
        Sheet sheet = new Sheet();
        sheet.setName(name);
        sheet.setDescription(description);
        sheet.setOwnerDepartment(dept);
        sheet.setTemplateId(templateId);
        sheet.setRowCount(clamp(def.rowCount(), 1, maxRows));
        sheet.setColCount(clamp(def.colCount(), 1, maxCols));
        sheet.setHeaderRows(clamp(def.headerRows(), 0, 20));
        sheet.setCreatedBy(userRepository.getReferenceById(user.id()));
        sheet = sheetRepository.save(sheet);

        for (ColumnDto c : def.columns()) {
            if (c.index() >= sheet.getColCount()) continue;
            SheetColumn col = new SheetColumn(sheet.getId(), c.index());
            col.setTitle(c.title());
            col.setWidth(c.width());
            col.setWatched(c.watched());
            col.setEditDepartmentList(c.editDepartments());
            columnRepository.save(col);
        }
        List<Cell> cells = new ArrayList<>();
        Instant now = Instant.now();
        for (CellDto c : def.cells()) {
            if (c.r() >= sheet.getRowCount() || c.c() >= sheet.getColCount()) continue;
            Cell cell = new Cell(sheet.getId(), c.r(), c.c());
            cell.setRawValue(emptyToNull(c.v()));
            cell.setStyle(emptyToNull(c.s()));
            cell.setUpdatedBy(user.id());
            cell.setUpdatedAt(now);
            if (!cell.isBlank()) cells.add(cell);
        }
        cellRepository.saveAll(cells);
        return sheet;
    }

    @Transactional
    public SheetDetail updateMeta(Long id, UpdateSheetRequest req, AuthUser user) {
        Sheet sheet = lock(id);
        AccessLevel level = permissions.require(user, sheet, AccessLevel.OWNER);
        sheet.setName(req.name());
        sheet.setDescription(req.description());
        if (req.headerRows() != null) sheet.setHeaderRows(req.headerRows());
        sheet.touch();
        publish(sheet, SheetEvent.Type.META_CHANGED, user, null);
        return views.detail(sheet, level, user);
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        Sheet sheet = lock(id);
        permissions.require(user, sheet, AccessLevel.OWNER);
        publish(sheet, SheetEvent.Type.SHEET_DELETED, user, null);
        sheetRepository.delete(sheet); // cells, columns, permissions, history cascade in the database
    }

    // ------------------------------------------------------------------ sharing & columns

    @Transactional
    public List<PermissionDto> setPermissions(Long id, SetPermissionsRequest req, AuthUser user) {
        Sheet sheet = lock(id);
        permissions.require(user, sheet, AccessLevel.OWNER);
        Map<Long, SheetPermission> existing = permissionRepository.findBySheetId(id).stream()
                .collect(Collectors.toMap(p -> p.getDepartment().getId(), Function.identity()));
        Set<Long> keep = new HashSet<>();
        List<Long> newlyShared = new ArrayList<>();
        for (PermissionEntry e : req.permissions()) {
            if (e.departmentId().equals(sheet.getOwnerDepartment().getId())) continue;
            if (e.level() == AccessLevel.NONE || e.level() == AccessLevel.OWNER) {
                if (e.level() == AccessLevel.OWNER) throw ApiException.badRequest("OWNER cannot be granted to other departments");
                continue;
            }
            Department dept = departmentRepository.findById(e.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
            keep.add(dept.getId());
            SheetPermission p = existing.get(dept.getId());
            if (p == null) {
                permissionRepository.save(new SheetPermission(id, dept, e.level()));
                newlyShared.add(dept.getId());
            } else if (p.getLevel() != e.level()) {
                p.setLevel(e.level());
                newlyShared.add(dept.getId());
            }
        }
        existing.values().stream().filter(p -> !keep.contains(p.getDepartment().getId())).forEach(permissionRepository::delete);
        sheet.touch();
        publish(sheet, SheetEvent.Type.META_CHANGED, user, null);
        if (!newlyShared.isEmpty()) publish(sheet, SheetEvent.Type.SHEET_SHARED, user, newlyShared);
        permissionRepository.flush();
        return views.permissions(id);
    }

    @Transactional
    public ColumnDto updateColumn(Long id, int col, UpdateColumnRequest req, AuthUser user) {
        Sheet sheet = lock(id);
        permissions.require(user, sheet, AccessLevel.OWNER);
        if (col < 0 || col >= sheet.getColCount()) throw ApiException.badRequest("Column out of range");
        List<String> depts = req.editDepartments() == null ? List.of() : req.editDepartments();
        for (String code : depts) {
            departmentRepository.findByCode(code).orElseThrow(() -> ApiException.badRequest("Unknown department " + code));
        }
        SheetColumn c = columnRepository.findBySheetIdAndColIndex(id, col).orElseGet(() -> new SheetColumn(id, col));
        c.setTitle(emptyToNull(req.title()));
        if (req.width() != null) c.setWidth(req.width());
        c.setWatched(req.watched());
        c.setEditDepartmentList(depts);
        columnRepository.save(c);
        sheet.touch();
        publish(sheet, SheetEvent.Type.META_CHANGED, user, null);
        return ColumnDto.of(c);
    }

    @Transactional
    public void setColumnWidth(Long id, int col, int width, AuthUser user) {
        Sheet sheet = lock(id);
        permissions.require(user, sheet, AccessLevel.EDIT);
        if (col < 0 || col >= sheet.getColCount()) throw ApiException.badRequest("Column out of range");
        SheetColumn c = columnRepository.findBySheetIdAndColIndex(id, col).orElseGet(() -> new SheetColumn(id, col));
        c.setWidth(width);
        columnRepository.save(c);
        sheet.touch();
        publish(sheet, SheetEvent.Type.META_CHANGED, user, null);
    }

    @Transactional
    public boolean setSubscribed(Long id, boolean subscribe, AuthUser user) {
        Sheet sheet = load(id);
        permissions.require(user, sheet, AccessLevel.VIEW);
        Optional<SheetSubscription> existing = subscriptionRepository.findBySheetIdAndUserId(id, user.id());
        if (subscribe && existing.isEmpty()) subscriptionRepository.save(new SheetSubscription(id, user.id()));
        if (!subscribe) existing.ifPresent(subscriptionRepository::delete);
        return subscribe;
    }

    // ------------------------------------------------------------------ structure (insert/delete rows & columns)

    @Transactional
    public SheetDetail changeStructure(Long id, StructureRequest req, AuthUser user) {
        Sheet sheet = lock(id);
        boolean colOp = switch (req.op()) {
            case INSERT_COLS, DELETE_COLS, APPEND_COLS -> true;
            default -> false;
        };
        boolean destructive = req.op() == StructureOp.DELETE_ROWS || req.op() == StructureOp.DELETE_COLS
                || req.op() == StructureOp.INSERT_ROWS || req.op() == StructureOp.INSERT_COLS;
        // Columns carry permissions/watch flags, so reshaping them is an owner action. Rows need EDIT.
        AccessLevel needed = colOp && destructive ? AccessLevel.OWNER : AccessLevel.EDIT;
        AccessLevel level = permissions.require(user, sheet, needed);
        int count = req.count();
        int index = req.index();

        switch (req.op()) {
            case APPEND_ROWS -> sheet.setRowCount(checkMax(sheet.getRowCount() + count, maxRows, "rows"));
            case APPEND_COLS -> sheet.setColCount(checkMax(sheet.getColCount() + count, maxCols, "columns"));
            case INSERT_ROWS -> {
                requireIndex(index, sheet.getRowCount());
                if (index < sheet.getHeaderRows() && level != AccessLevel.OWNER) throw ApiException.forbidden("Cannot insert into header rows");
                sheet.setRowCount(checkMax(sheet.getRowCount() + count, maxRows, "rows"));
                cellRepository.shiftRowsDown(id, index, count);
                rewriteFormulas(id, f -> FormulaShifter.insert(f, FormulaShifter.Axis.ROW, index, count));
            }
            case DELETE_ROWS -> {
                requireIndex(index, sheet.getRowCount() - 1);
                int n = Math.min(count, sheet.getRowCount() - index);
                if (index < sheet.getHeaderRows() && level != AccessLevel.OWNER) throw ApiException.forbidden("Cannot delete header rows");
                if (n >= sheet.getRowCount()) throw ApiException.badRequest("A sheet needs at least one row");
                cellRepository.deleteRows(id, index, index + n);
                cellRepository.shiftRowsUp(id, index + n, n);
                sheet.setRowCount(sheet.getRowCount() - n);
                rewriteFormulas(id, f -> FormulaShifter.delete(f, FormulaShifter.Axis.ROW, index, n));
            }
            case INSERT_COLS -> {
                requireIndex(index, sheet.getColCount());
                sheet.setColCount(checkMax(sheet.getColCount() + count, maxCols, "columns"));
                cellRepository.shiftColsRight(id, index, count);
                columnRepository.shiftRight(id, index, count);
                rewriteFormulas(id, f -> FormulaShifter.insert(f, FormulaShifter.Axis.COL, index, count));
            }
            case DELETE_COLS -> {
                requireIndex(index, sheet.getColCount() - 1);
                int n = Math.min(count, sheet.getColCount() - index);
                if (n >= sheet.getColCount()) throw ApiException.badRequest("A sheet needs at least one column");
                cellRepository.deleteCols(id, index, index + n);
                cellRepository.shiftColsLeft(id, index + n, n);
                columnRepository.deleteRange(id, index, index + n);
                columnRepository.shiftLeft(id, index + n, n);
                sheet.setColCount(sheet.getColCount() - n);
                rewriteFormulas(id, f -> FormulaShifter.delete(f, FormulaShifter.Axis.COL, index, n));
            }
        }
        sheet.touch();
        sheet = sheetRepository.save(sheet); // bulk queries cleared the persistence context
        SheetEvent e = new SheetEvent(SheetEvent.Type.STRUCTURE_CHANGED, sheet.getId(), sheet.getName(), sheet.getVersion(),
                UUID.randomUUID().toString(), req.clientId(), user.id(), user.displayName(), user.departmentCode(),
                user.departmentName(), List.of(), new SheetEvent.StructurePayload(req.op().name(), index, count), null, Instant.now());
        events.publish(e);
        return views.detail(sheet, level, user);
    }

    private void rewriteFormulas(Long sheetId, Function<String, String> fn) {
        List<Cell> formulas = cellRepository.findFormulaCells(sheetId);
        for (Cell c : formulas) {
            String updated = fn.apply(c.getRawValue());
            if (!Objects.equals(updated, c.getRawValue())) c.setRawValue(updated);
        }
        cellRepository.saveAll(formulas);
    }

    // ------------------------------------------------------------------ helpers

    public Sheet load(Long id) {
        return sheetRepository.findById(id).orElseThrow(() -> ApiException.notFound("Sheet"));
    }

    public Sheet lock(Long id) {
        return sheetRepository.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Sheet"));
    }

    public void requireCreator(AuthUser user) {
        if (user.role() == Role.MEMBER) throw ApiException.forbidden("Only department managers can create sheets");
    }

    public Department resolveOwnerDepartment(Long requested, AuthUser user) {
        if (requested == null || requested.equals(user.departmentId())) {
            return departmentRepository.findById(user.departmentId()).orElseThrow();
        }
        if (user.role() != Role.ADMIN) throw ApiException.forbidden("You can only create sheets for your own department");
        return departmentRepository.findById(requested).orElseThrow(() -> ApiException.notFound("Department"));
    }

    private void publish(Sheet sheet, SheetEvent.Type type, AuthUser user, List<Long> sharedWith) {
        events.publish(new SheetEvent(type, sheet.getId(), sheet.getName(), sheet.getVersion(), UUID.randomUUID().toString(),
                null, user.id(), user.displayName(), user.departmentCode(), user.departmentName(), List.of(), null,
                sharedWith, Instant.now()));
    }

    private static void requireIndex(int index, int maxInclusive) {
        if (index < 0 || index > maxInclusive) throw ApiException.badRequest("Index out of range");
    }

    private static int checkMax(int value, int max, String what) {
        if (value > max) throw ApiException.badRequest("A sheet can have at most " + max + " " + what);
        return value;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String emptyToNull(String s) {
        return s == null || s.isEmpty() || "{}".equals(s) ? null : s;
    }
}
