package com.factory.collab.dto;

import java.time.Instant;
import java.util.List;

import com.factory.collab.domain.AccessLevel;
import com.factory.collab.domain.Cell;
import com.factory.collab.domain.Department;
import com.factory.collab.domain.Notification;
import com.factory.collab.domain.Role;
import com.factory.collab.domain.SheetColumn;
import com.factory.collab.domain.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request / response payloads of the REST API, grouped in one place. */
public final class Dtos {
    private Dtos() {
    }

    // ---------- auth & users ----------
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, UserDto user) {
    }

    public record DepartmentDto(Long id, String code, String name, String description) {
        public static DepartmentDto of(Department d) {
            return d == null ? null : new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getDescription());
        }
    }

    public record UserDto(Long id, String username, String displayName, Role role, boolean enabled,
            DepartmentDto department) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                    DepartmentDto.of(u.getDepartment()));
        }
    }

    public record SaveUserRequest(@NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 100) String displayName,
            @Size(min = 6, max = 100) String password,
            @NotNull Long departmentId,
            @NotNull Role role,
            Boolean enabled) {
    }

    public record SaveDepartmentRequest(@NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9_]+") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 6) String newPassword) {
    }

    // ---------- sheets ----------
    public record ColumnDto(int index, String title, Integer width, boolean watched, List<String> editDepartments) {
        public static ColumnDto of(SheetColumn c) {
            return new ColumnDto(c.getColIndex(), c.getTitle(), c.getWidth(), c.isWatched(), c.editDepartmentList());
        }
    }

    public record CellDto(int r, int c, String v, String s) {
        public static CellDto of(Cell cell) {
            return new CellDto(cell.getRowIdx(), cell.getColIdx(), cell.getRawValue(), cell.getStyle());
        }
    }

    public record PermissionDto(Long departmentId, String departmentCode, String departmentName, AccessLevel level) {
    }

    public record SheetSummary(Long id, String name, String description, DepartmentDto ownerDepartment,
            int rowCount, int colCount, long version, Instant updatedAt,
            AccessLevel access, boolean subscribed, List<PermissionDto> sharedWith,
            long changesLast24h, int tabCount) {
    }

    /** One tab of a multi-sheet workbook. */
    public record TabDto(Long id, String name, int index) {
    }

    public record SheetDetail(Long id, String name, String description, DepartmentDto ownerDepartment,
            int rowCount, int colCount, int headerRows, long version, Instant updatedAt,
            AccessLevel access, boolean subscribed,
            List<ColumnDto> columns, List<CellDto> cells, List<PermissionDto> sharedWith,
            Long workbookId, String tabName, List<TabDto> tabs) {
    }

    public record CreateSheetRequest(@NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            Long departmentId,
            Long templateId,
            @Min(1) Integer rows,
            @Min(1) Integer cols) {
    }

    public record UpdateSheetRequest(@NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @Min(0) @Max(20) Integer headerRows) {
    }

    /**
     * Full new state of one cell: value {@code v} (null/empty clears) and style
     * JSON {@code s}.
     */
    public record CellEdit(@Min(0) int r, @Min(0) int c, @Size(max = 32000) String v, @Size(max = 500) String s) {
    }

    public record UpdateCellsRequest(String clientId, @NotEmpty @Size(max = 20000) List<@Valid CellEdit> edits) {
    }

    public record UpdateCellsResponse(long version, int applied) {
    }

    public enum StructureOp {
        INSERT_ROWS, DELETE_ROWS, INSERT_COLS, DELETE_COLS, APPEND_ROWS, APPEND_COLS
    }

    public record StructureRequest(@NotNull StructureOp op, @Min(0) int index, @Min(1) @Max(1000) int count,
            String clientId) {
    }

    public record UpdateColumnRequest(@Size(max = 120) String title, @Min(30) @Max(1000) Integer width,
            boolean watched, List<String> editDepartments) {
    }

    /** Add a worksheet (name optional, auto-generated when blank) or rename one. */
    public record TabRequest(@Size(max = 100) String name, String clientId) {
    }

    public record ColumnWidthRequest(@Min(30) @Max(1000) int width) {
    }

    public record SetPermissionsRequest(@NotNull List<@Valid PermissionEntry> permissions) {
    }

    public record PermissionEntry(@NotNull Long departmentId, @NotNull AccessLevel level) {
    }

    // ---------- history / activity ----------
    public record ChangeDto(Long id, Long sheetId, String sheetName, String batchId, int row, int col, String cellRef,
            String columnLabel, String rowLabel, String oldValue, String newValue, boolean watched,
            String userName, String departmentCode, String departmentName, Instant changedAt,
            List<AckDto> acknowledgements) {
    }

    /**
     * Who received an IMPORTANT notification for a change, and whether/when they
     * acknowledged it.
     */
    public record AckDto(String userName, String departmentName, Instant acknowledgedAt) {
    }

    // ---------- notifications ----------
    public record NotificationDto(Long id, Long sheetId, String batchId, Notification.Type type,
            Notification.Severity severity, String title, String message,
            String actorName, String actorDepartment, Instant createdAt, Instant readAt) {
        public static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getSheetId(), n.getBatchId(), n.getType(), n.getSeverity(),
                    n.getTitle(), n.getMessage(), n.getActorName(), n.getActorDepartment(), n.getCreatedAt(),
                    n.getReadAt());
        }
    }

    public record NotificationCount(long unread, long importantUnread) {
    }

    // ---------- templates ----------
    public record TemplateDto(Long id, String name, String description, DepartmentDto department,
            String createdBy, Instant createdAt, TemplateDefinition definition) {
    }

    public record SaveTemplateRequest(@NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Long departmentId,
            @NotNull @Valid TemplateDefinition definition) {
    }

    public record SaveAsTemplateRequest(@NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            boolean includeData) {
    }

    // ---------- dashboard ----------
    public record DepartmentPanel(DepartmentDto department, List<SheetSummary> sheets, long changesLast24h) {
    }

    public record DashboardDto(UserDto me, List<DepartmentPanel> departments, List<ChangeDto> recentChanges,
            List<ChangeDto> watchedChanges, List<NotificationDto> importantUnread,
            NotificationCount notifications) {
    }
}
