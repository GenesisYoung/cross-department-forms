package com.factory.collab.service;

import com.factory.collab.i18n.Messages;
import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.repo.SheetPermissionRepository;
import com.factory.collab.repo.SheetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Permission model:
 * <ul>
 *   <li>ADMIN: OWNER of everything.</li>
 *   <li>Owner department: MANAGER = OWNER (sharing, columns, delete), MEMBER = EDIT.</li>
 *   <li>Other departments: whatever the owner granted (VIEW / EDIT), else NONE (sheet invisible).</li>
 *   <li>Column rule: if a column lists edit departments, only those departments (and owners) may edit it —
 *       this also lets a VIEW department edit just "its" column (e.g. PMC's "Required By" in Purchase's sheet).</li>
 *   <li>Header rows are editable by owners only.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final SheetRepository sheetRepository;
    private final SheetPermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public AccessLevel levelFor(AuthUser user, Long sheetId) {
        return sheetRepository.findById(sheetId).map(s -> levelFor(user, s)).orElse(AccessLevel.NONE);
    }

    public AccessLevel levelFor(AuthUser user, Sheet sheet) {
        if (user.role() == Role.ADMIN) return AccessLevel.OWNER;
        if (sheet.getOwnerDepartment().getId().equals(user.departmentId())) {
            return user.role() == Role.MANAGER ? AccessLevel.OWNER : AccessLevel.EDIT;
        }
        return permissionRepository.findBySheetIdAndDepartmentId(sheet.getId(), user.departmentId())
                .map(SheetPermission::getLevel)
                .orElse(AccessLevel.NONE);
    }

    /** Level per sheet for a list of sheets, using one permission query. */
    public Map<Long, AccessLevel> levelsFor(AuthUser user, Collection<Sheet> sheets) {
        Map<Long, AccessLevel> granted = new HashMap<>();
        if (user.role() != Role.ADMIN) {
            for (SheetPermission p : permissionRepository.findByDepartmentId(user.departmentId())) {
                granted.put(p.getSheetId(), p.getLevel());
            }
        }
        Map<Long, AccessLevel> out = new HashMap<>();
        for (Sheet s : sheets) {
            AccessLevel level;
            if (user.role() == Role.ADMIN) level = AccessLevel.OWNER;
            else if (s.getOwnerDepartment().getId().equals(user.departmentId()))
                level = user.role() == Role.MANAGER ? AccessLevel.OWNER : AccessLevel.EDIT;
            else level = granted.getOrDefault(s.getId(), AccessLevel.NONE);
            out.put(s.getId(), level);
        }
        return out;
    }

    public AccessLevel require(AuthUser user, Sheet sheet, AccessLevel needed) {
        AccessLevel level = levelFor(user, sheet);
        if (level == AccessLevel.NONE) throw ApiException.notFound("Sheet");
        if (!level.atLeast(needed)) {
            throw ApiException.forbidden("error.accessRequired", Messages.text("level." + needed), sheet.getName());
        }
        return level;
    }

    /** Null if the edit is allowed, otherwise a human-readable reason. */
    public String denyReason(AccessLevel level, String userDeptCode, Sheet sheet, SheetColumn column, int row) {
        if (level == AccessLevel.OWNER) return null;
        if (row < sheet.getHeaderRows()) return Messages.text("deny.header");
        List<String> colDepts = column == null ? List.of() : column.editDepartmentList();
        if (!colDepts.isEmpty()) {
            return colDepts.contains(userDeptCode) ? null : Messages.text("deny.column", String.join(", ", colDepts));
        }
        return level.atLeast(AccessLevel.EDIT) ? null : Messages.text("deny.view");
    }

    /** Departments that can see the sheet: the owner plus every department granted VIEW or EDIT. */
    public Set<Long> departmentsWithAccess(Sheet sheet) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(sheet.getOwnerDepartment().getId());
        for (SheetPermission p : permissionRepository.findBySheetId(sheet.getId())) {
            if (p.getLevel().atLeast(AccessLevel.VIEW)) ids.add(p.getDepartment().getId());
        }
        return ids;
    }

    public boolean canSee(User user, Sheet sheet) {
        return levelFor(AuthUser.of(user), sheet).atLeast(AccessLevel.VIEW);
    }
}
