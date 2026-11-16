package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.NotificationCount;
import com.factory.collab.dto.Dtos.NotificationDto;
import com.factory.collab.messaging.EventPublisher;
import com.factory.collab.messaging.SheetEvent;
import com.factory.collab.messaging.UserPush;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Decides who must hear about a change:
 * <ul>
 *   <li>Watched column changed → IMPORTANT notification to every user of every department that can see the
 *       sheet (except the editor). Reading it counts as acknowledgement and is shown in the change history.</li>
 *   <li>Any change → INFO notification to users who subscribed to the sheet.</li>
 *   <li>Sheet shared with a department → INFO to that department.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MAX_LINES = 8;

    private final NotificationRepository notificationRepository;
    private final SheetRepository sheetRepository;
    private final SheetSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PermissionService permissions;
    private final EventPublisher events;

    @Transactional
    public void handle(SheetEvent e) {
        Optional<Sheet> maybeSheet = sheetRepository.findById(e.sheetId());
        if (maybeSheet.isEmpty()) return; // deleted meanwhile
        Sheet sheet = maybeSheet.get();
        if (e.batchId() != null && !notificationRepository.findByBatchIdIn(List.of(e.batchId())).isEmpty()) {
            return; // redelivered message, already processed
        }
        List<Notification> created = switch (e.type()) {
            case CELLS_UPDATED -> forCellChanges(sheet, e);
            case STRUCTURE_CHANGED -> forSubscribers(sheet, e, Notification.Type.STRUCTURE_CHANGED,
                    "Layout changed in " + sheet.label(), describeStructure(e));
            case SHEET_SHARED -> forShare(sheet, e);
            default -> List.of();
        };
        if (created.isEmpty()) return;
        notificationRepository.saveAll(created);

        Map<Long, String> usernames = new HashMap<>();
        userRepository.findAllById(created.stream().map(Notification::getRecipientId).distinct().toList())
                .forEach(u -> usernames.put(u.getId(), u.getUsername()));
        events.publish(new UserPush(created.stream()
                .filter(n -> usernames.containsKey(n.getRecipientId()))
                .map(n -> new UserPush.Item(usernames.get(n.getRecipientId()), NotificationDto.of(n)))
                .toList()));
    }

    private List<Notification> forCellChanges(Sheet sheet, SheetEvent e) {
        List<SheetEvent.CellChangePayload> valueChanges = e.changes().stream().filter(SheetEvent.CellChangePayload::valueChanged).toList();
        if (valueChanges.isEmpty()) return List.of();
        List<SheetEvent.CellChangePayload> watched = valueChanges.stream().filter(SheetEvent.CellChangePayload::watched).toList();

        List<Notification> out = new ArrayList<>();
        Set<Long> notified = new HashSet<>();
        notified.add(e.actorId());

        if (!watched.isEmpty()) {
            String title = "⚠ " + watched.size() + " key change" + (watched.size() > 1 ? "s" : "") + " in " + sheet.label();
            String body = describeChanges(watched);
            for (User u : userRepository.findByDepartmentIdInAndEnabledTrue(permissions.departmentsWithAccess(sheet))) {
                if (notified.add(u.getId())) {
                    out.add(build(u.getId(), sheet, e, Notification.Type.CELLS_CHANGED, Notification.Severity.IMPORTANT, title, body));
                }
            }
        }
        String title = valueChanges.size() + " cell" + (valueChanges.size() > 1 ? "s" : "") + " updated in " + sheet.label();
        String body = describeChanges(valueChanges);
        for (User u : subscribers(sheet)) {
            if (notified.add(u.getId())) {
                out.add(build(u.getId(), sheet, e, Notification.Type.CELLS_CHANGED, Notification.Severity.INFO, title, body));
            }
        }
        return out;
    }

    private List<Notification> forSubscribers(Sheet sheet, SheetEvent e, Notification.Type type, String title, String body) {
        List<Notification> out = new ArrayList<>();
        for (User u : subscribers(sheet)) {
            if (!u.getId().equals(e.actorId())) out.add(build(u.getId(), sheet, e, type, Notification.Severity.INFO, title, body));
        }
        return out;
    }

    private List<Notification> forShare(Sheet sheet, SheetEvent e) {
        if (e.newlySharedDepartmentIds() == null || e.newlySharedDepartmentIds().isEmpty()) return List.of();
        List<Notification> out = new ArrayList<>();
        for (User u : userRepository.findByDepartmentIdInAndEnabledTrue(e.newlySharedDepartmentIds())) {
            if (u.getId().equals(e.actorId())) continue;
            AccessLevel level = permissions.levelFor(AuthUser.of(u), sheet);
            if (!level.atLeast(AccessLevel.VIEW)) continue;
            out.add(build(u.getId(), sheet, e, Notification.Type.SHEET_SHARED, Notification.Severity.INFO,
                    e.actorDepartmentName() + " shared \"" + sheet.getName() + "\" with your department",
                    "Your department now has " + level + " access."));
        }
        return out;
    }

    private List<User> subscribers(Sheet sheet) {
        List<Long> ids = subscriptionRepository.findBySheetId(sheet.getId()).stream().map(SheetSubscription::getUserId).toList();
        if (ids.isEmpty()) return List.of();
        return userRepository.findByIdInAndEnabledTrue(ids).stream().filter(u -> permissions.canSee(u, sheet)).toList();
    }

    private Notification build(Long recipient, Sheet sheet, SheetEvent e, Notification.Type type,
                               Notification.Severity severity, String title, String message) {
        Notification n = new Notification();
        n.setRecipientId(recipient);
        n.setSheetId(sheet.getId());
        n.setBatchId(e.batchId());
        n.setType(type);
        n.setSeverity(severity);
        n.setTitle(SheetLabels.truncate(title, 255));
        n.setMessage(message);
        n.setActorName(e.actorName());
        n.setActorDepartment(e.actorDepartmentName());
        n.setCreatedAt(e.at() == null ? Instant.now() : e.at());
        return n;
    }

    static String describeChanges(List<SheetEvent.CellChangePayload> changes) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (SheetEvent.CellChangePayload c : changes) {
            if (shown == MAX_LINES) {
                sb.append("…and ").append(changes.size() - MAX_LINES).append(" more");
                break;
            }
            sb.append(c.rowLabel()).append(" · ").append(c.columnLabel()).append(": ")
                    .append(display(c.old())).append(" → ").append(display(c.v())).append('\n');
            shown++;
        }
        return sb.toString().trim();
    }

    private static String describeStructure(SheetEvent e) {
        SheetEvent.StructurePayload s = e.structure();
        if (s == null) return "The sheet layout changed.";
        String what = switch (s.op()) {
            case "INSERT_ROWS" -> "inserted " + s.count() + " row(s) at row " + (s.index() + 1);
            case "DELETE_ROWS" -> "deleted " + s.count() + " row(s) from row " + (s.index() + 1);
            case "INSERT_COLS" -> "inserted " + s.count() + " column(s) at " + CellRefs.columnName(s.index());
            case "DELETE_COLS" -> "deleted " + s.count() + " column(s) from " + CellRefs.columnName(s.index());
            case "APPEND_ROWS" -> "added " + s.count() + " row(s)";
            default -> "added " + s.count() + " column(s)";
        };
        return e.actorName() + " (" + e.actorDepartmentName() + ") " + what + ".";
    }

    private static String display(String v) {
        if (v == null || v.isEmpty()) return "(empty)";
        return SheetLabels.truncate(v, 60);
    }

    // ------------------------------------------------------------------ user-facing API

    @Transactional(readOnly = true)
    public List<NotificationDto> list(AuthUser user, boolean unreadOnly, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return (unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(user.id(), p)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.id(), p))
                .map(NotificationDto::of).getContent();
    }

    @Transactional(readOnly = true)
    public NotificationCount count(AuthUser user) {
        return new NotificationCount(notificationRepository.countByRecipientIdAndReadAtIsNull(user.id()),
                notificationRepository.countByRecipientIdAndReadAtIsNullAndSeverity(user.id(), Notification.Severity.IMPORTANT));
    }

    @Transactional
    public NotificationDto markRead(Long id, AuthUser user) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getRecipientId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
        return NotificationDto.of(n);
    }

    @Transactional
    public int markAllRead(AuthUser user) {
        return notificationRepository.markAllRead(user.id(), Instant.now());
    }
}
