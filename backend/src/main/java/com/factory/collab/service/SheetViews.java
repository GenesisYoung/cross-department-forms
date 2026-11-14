package com.factory.collab.service;

import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Builds API views of sheets and change records. */
@Component
@RequiredArgsConstructor
public class SheetViews {
    private final SheetPermissionRepository permissionRepository;
    private final SheetSubscriptionRepository subscriptionRepository;
    private final CellChangeRepository changeRepository;
    private final SheetColumnRepository columnRepository;
    private final CellRepository cellRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<PermissionDto> permissions(Long sheetId) {
        return permissionRepository.findBySheetId(sheetId).stream()
                .map(p -> new PermissionDto(p.getDepartment().getId(), p.getDepartment().getCode(),
                        p.getDepartment().getName(), p.getLevel()))
                .sorted(Comparator.comparing(PermissionDto::departmentName))
                .toList();
    }

    public SheetSummary summary(Sheet s, AccessLevel level, AuthUser user) {
        boolean subscribed = subscriptionRepository.findBySheetIdAndUserId(s.getId(), user.id()).isPresent();
        long recent = changeRepository.countSince(s.getId(), Instant.now().minus(24, ChronoUnit.HOURS));
        return new SheetSummary(s.getId(), s.getName(), s.getDescription(), DepartmentDto.of(s.getOwnerDepartment()),
                s.getRowCount(), s.getColCount(), s.getVersion(), s.getUpdatedAt(), level, subscribed,
                permissions(s.getId()), recent);
    }

    public SheetDetail detail(Sheet s, AccessLevel level, AuthUser user) {
        boolean subscribed = subscriptionRepository.findBySheetIdAndUserId(s.getId(), user.id()).isPresent();
        List<ColumnDto> cols = columnRepository.findBySheetIdOrderByColIndex(s.getId()).stream().map(ColumnDto::of).toList();
        List<CellDto> cells = cellRepository.findBySheetId(s.getId()).stream().map(CellDto::of).toList();
        return new SheetDetail(s.getId(), s.getName(), s.getDescription(), DepartmentDto.of(s.getOwnerDepartment()),
                s.getRowCount(), s.getColCount(), s.getHeaderRows(), s.getVersion(), s.getUpdatedAt(), level, subscribed,
                cols, cells, permissions(s.getId()));
    }

    /** Change records, with acknowledgement status attached to watched changes when requested. */
    public List<ChangeDto> changes(List<CellChange> changes, Map<Long, String> sheetNames, boolean withAcks) {
        Map<String, List<AckDto>> acks = new HashMap<>();
        if (withAcks) {
            Set<String> batches = new HashSet<>();
            changes.stream().filter(CellChange::isWatched).forEach(c -> batches.add(c.getBatchId()));
            if (!batches.isEmpty()) {
                List<Notification> notes = notificationRepository.findByBatchIdIn(batches).stream()
                        .filter(n -> n.getSeverity() == Notification.Severity.IMPORTANT).toList();
                Map<Long, User> users = new HashMap<>();
                userRepository.findAllById(notes.stream().map(Notification::getRecipientId).distinct().toList())
                        .forEach(u -> users.put(u.getId(), u));
                for (Notification n : notes) {
                    User u = users.get(n.getRecipientId());
                    if (u == null) continue;
                    acks.computeIfAbsent(n.getBatchId(), k -> new ArrayList<>())
                            .add(new AckDto(u.getDisplayName(), u.getDepartment().getName(), n.getReadAt()));
                }
            }
        }
        return changes.stream().map(c -> new ChangeDto(c.getId(), c.getSheetId(), sheetNames.get(c.getSheetId()),
                c.getBatchId(), c.getRowIdx(), c.getColIdx(), CellRefs.ref(c.getRowIdx(), c.getColIdx()),
                c.getColumnLabel(), c.getRowLabel(), c.getOldValue(), c.getNewValue(), c.isWatched(),
                c.getUser().getDisplayName(), c.getDepartment().getCode(), c.getDepartment().getName(),
                c.getChangedAt(), c.isWatched() ? acks.getOrDefault(c.getBatchId(), List.of()) : List.of())).toList();
    }
}
