package com.factory.collab.service;

import com.factory.collab.config.AuthUser;
import com.factory.collab.i18n.Messages;
import com.factory.collab.i18n.NotificationTexts;
import org.springframework.context.i18n.LocaleContextHolder;
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
        return summary(s, List.of(s), level, user);
    }

    /** Summary of a sheet, or of a whole workbook when {@code tabs} holds all of its tabs (first tab first). */
    public SheetSummary summary(Sheet s, List<Sheet> tabs, AccessLevel level, AuthUser user) {
        boolean subscribed = subscriptionRepository.findBySheetIdAndUserId(s.getId(), user.id()).isPresent();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long recent = 0;
        long version = 0;
        Instant updatedAt = s.getUpdatedAt();
        for (Sheet t : tabs) {
            recent += changeRepository.countSince(t.getId(), since);
            version += t.getVersion();
            if (t.getUpdatedAt().isAfter(updatedAt)) updatedAt = t.getUpdatedAt();
        }
        return new SheetSummary(s.getId(), s.getName(), s.getDescription(), DepartmentDto.of(s.getOwnerDepartment()),
                s.getRowCount(), s.getColCount(), version, updatedAt, level, subscribed,
                permissions(s.getId()), recent, tabs.size());
    }

    public SheetDetail detail(Sheet s, AccessLevel level, AuthUser user) {
        return detail(s, List.of(s), level, user);
    }

    public SheetDetail detail(Sheet s, List<Sheet> tabs, AccessLevel level, AuthUser user) {
        boolean subscribed = subscriptionRepository.findBySheetIdAndUserId(s.getId(), user.id()).isPresent();
        List<ColumnDto> cols = columnRepository.findBySheetIdOrderByColIndex(s.getId()).stream().map(ColumnDto::of).toList();
        List<CellDto> cells = cellRepository.findBySheetId(s.getId()).stream().map(CellDto::of).toList();
        return new SheetDetail(s.getId(), s.getName(), s.getDescription(), DepartmentDto.of(s.getOwnerDepartment()),
                s.getRowCount(), s.getColCount(), s.getHeaderRows(), s.getVersion(), s.getUpdatedAt(), level, subscribed,
                cols, cells, permissions(s.getId()), s.getWorkbookId(), s.getTabName(),
                s.inWorkbook() ? tabs.stream().map(t -> new TabDto(t.getId(), t.getTabName(), t.getTabIndex())).toList() : List.of());
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
                c.getColumnLabel(), NotificationTexts.rowLabel(c.getRowLabel(), LocaleContextHolder.getLocale()), c.getOldValue(), c.getNewValue(), c.isWatched(),
                c.getUser().getDisplayName(), c.getDepartment().getCode(), c.getDepartment().getName(),
                c.getChangedAt(), c.isWatched() ? acks.getOrDefault(c.getBatchId(), List.of()) : List.of(),
                rowLabels(c.getRowLabel()))).toList();
    }

    private Map<String, String> rowLabels(String label) {
        if (label == null) return Map.of();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Locale language : Messages.SUPPORTED)
            labels.put(language.toLanguageTag(), NotificationTexts.rowLabel(label, language));
        return labels;
    }
}
