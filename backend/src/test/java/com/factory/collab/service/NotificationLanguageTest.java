package com.factory.collab.service;

import com.factory.collab.domain.*;
import com.factory.collab.i18n.*;
import com.factory.collab.messaging.*;
import com.factory.collab.repo.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationLanguageTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SheetRepository sheets = mock(SheetRepository.class);
    private final SheetSubscriptionRepository subscriptions = mock(SheetSubscriptionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final NotificationService service = new NotificationService(notifications, sheets, subscriptions, users, permissions, events);
    private final Sheet sheet = new Sheet();
    private final User recipient = new User();

    @BeforeEach void setup() {
        Department department = new Department("PMC", "English department", ""); department.setId(1L);
        sheet.setId(1L); sheet.setName("English sheet"); sheet.setOwnerDepartment(department);
        recipient.setId(2L); recipient.setUsername("recipient"); recipient.setDepartment(department); recipient.setRole(Role.MEMBER);
        when(sheets.findById(1L)).thenReturn(Optional.of(sheet));
        when(users.findAllById(any())).thenReturn(List.of(recipient));
        when(users.findByDepartmentIdInAndEnabledTrue(any())).thenReturn(List.of(recipient));
        when(permissions.departmentsWithAccess(sheet)).thenReturn(Set.of(1L));
        LocaleContextHolder.setLocale(Locale.TRADITIONAL_CHINESE); // editor/worker locale must not choose recipient text
    }
    @AfterEach void resetLocale() { LocaleContextHolder.resetLocaleContext(); }
    @Test void producesAllLanguagesForWatchedChangesAndPushesTheSameSnapshots() {
        var change = new SheetEvent.CellChangePayload(1, 0, "English value", null, "", true, true, "Part", "Row 2 (SKU)", "A2");
        service.handle(event(SheetEvent.Type.CELLS_UPDATED, List.of(change), null, null));
        Notification n = saved();
        var texts = NotificationTexts.all(n);
        assertEquals("⚠ 1 key change in English sheet", n.getTitle());
        assertEquals("⚠ English sheet 有 1 项关键变更", texts.get("zh-CN").title());
        assertEquals("列 2 (SKU) · Part: （空） → English value", texts.get("zh-TW").message());
        ArgumentCaptor<UserPush> push = ArgumentCaptor.forClass(UserPush.class);
        verify(events).publish(push.capture());
        assertEquals(texts, push.getValue().items().get(0).notification().translations());
        assertEquals(2L, n.getRecipientId());
    }
    @Test void translatesStructureAndSharingPerRecipientPermission() {
        SheetSubscription sub = new SheetSubscription(); sub.setUserId(2L);
        when(subscriptions.findBySheetId(1L)).thenReturn(List.of(sub));
        when(users.findByIdInAndEnabledTrue(any())).thenReturn(List.of(recipient));
        when(permissions.canSee(recipient, sheet)).thenReturn(true);
        service.handle(event(SheetEvent.Type.STRUCTURE_CHANGED, List.of(), new SheetEvent.StructurePayload("INSERT_COLS", 0, 2), null));
        assertEquals("Alice（English department）在 A 欄插入了 2 欄。", NotificationTexts.all(saved()).get("zh-TW").message());
        clearInvocations(notifications);
        when(permissions.levelFor(any(), eq(sheet))).thenReturn(AccessLevel.VIEW);
        service.handle(event(SheetEvent.Type.SHEET_SHARED, List.of(), null, List.of(1L)));
        assertEquals("您的部门现在具有查看权限。", NotificationTexts.all(saved()).get("zh-CN").message());
    }
    private Notification saved() {
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        return captor.getValue().get(0);
    }
    private SheetEvent event(SheetEvent.Type type, List<SheetEvent.CellChangePayload> changes, SheetEvent.StructurePayload structure, List<Long> departments) {
        return new SheetEvent(type, 1L, "English sheet", 1L, UUID.randomUUID().toString(), "client", 1L, "Alice", "PMC",
                "English department", changes, structure, departments, Instant.now());
    }
}
