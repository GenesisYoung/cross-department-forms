package com.factory.collab.i18n;

import com.factory.collab.domain.Notification;
import com.factory.collab.dto.Dtos.NotificationDto;
import org.junit.jupiter.api.*;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); }
    @Test void negotiatesSupportedLanguagesAndIgnoresHostDefault() {
        assertEquals(Locale.TRADITIONAL_CHINESE, Messages.resolve("zh-Hant-HK, en;q=0.8"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, Messages.resolve("fr, zh-CN;q=0.9"));
        assertEquals(Locale.ENGLISH, Messages.resolve("zh-CN;q=0, en;q=0.5"));
        assertEquals(Locale.ENGLISH, Messages.resolve("fr"));
        assertEquals(Locale.ENGLISH, Messages.resolve("invalid;q=oops"));
        assertEquals(Locale.ENGLISH, Messages.resolve(null));
    }
    @Test void catalogsHaveIdenticalKeysAndSafeParameters() {
        var control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        Set<String> keys = ResourceBundle.getBundle("messages", Locale.ENGLISH, control).keySet();
        for (String suffix : List.of("messages.properties", "messages_zh_CN.properties", "messages_zh_TW.properties")) {
            Properties properties = new Properties();
            try (var input = getClass().getClassLoader().getResourceAsStream(suffix)) {
                properties.load(new java.io.InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            assertEquals(keys, properties.stringPropertyNames());
            properties.forEach((k,v) -> assertFalse(v.toString().isBlank(), k.toString()));
        }
        String userData = "$1\\raw {1} O'Brien";
        assertEquals("Unknown department " + userData, Messages.text(Locale.ENGLISH, "error.unknownDepartment", userData));
        assertEquals("A department's code cannot be changed", Messages.text(Locale.ENGLISH, "error.departmentCodeImmutable"));
    }
    @Test void readsSnapshotsAndLegacyMessagesWithoutChangingValues() {
        Notification n = new Notification(); n.setType(Notification.Type.SHEET_SHARED);
        n.setActorDepartment("PMC"); n.setTitle("PMC shared \"English sheet\" with your department");
        n.setMessage("Your department now has EDIT access.");
        var legacy = NotificationTexts.all(n);
        assertEquals("PMC 向您的部门共享了“English sheet”", legacy.get("zh-CN").title());
        assertEquals("您的部門現在具有編輯權限。", legacy.get("zh-TW").message());
        n.setLocalizedText(NotificationTexts.encode(legacy));
        LocaleContextHolder.setLocale(Locale.TRADITIONAL_CHINESE);
        NotificationDto dto = NotificationDto.of(n);
        assertEquals(legacy.get("zh-TW").title(), dto.title());
        assertEquals(3, dto.translations().size());
        n.setType(Notification.Type.CELLS_CHANGED); n.setLocalizedText(null);
        n.setTitle("2 cells updated in English sheet"); n.setMessage("Row 2 · Part: English value → (empty)\n…and 2 more");
        assertEquals("Row 2 · Part: English value → (empty)\n…另有 2 项", NotificationTexts.all(n).get("zh-CN").message());
        n.setLocalizedText("broken json");
        assertNotNull(NotificationTexts.all(n).get("en"));
    }
}
