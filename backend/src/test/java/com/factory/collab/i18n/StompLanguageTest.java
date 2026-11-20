package com.factory.collab.i18n;

import com.factory.collab.config.*;
import com.factory.collab.domain.*;
import com.factory.collab.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompLanguageTest {
    @Test void authenticatesAndUsesConnectionLanguageForLaterErrors() {
        TokenAuthenticator auth = mock(TokenAuthenticator.class);
        PermissionService permissions = mock(PermissionService.class);
        StompAuthInterceptor interceptor = new StompAuthInterceptor(auth, permissions);
        AuthUser user = new AuthUser(1L, "alice", "Alice", 1L, "PMC", "PMC", Role.MEMBER);
        var principal = new UsernamePasswordAuthenticationToken(user, null, List.of());
        when(auth.authenticate("token")).thenReturn(Optional.of(principal));
        when(permissions.levelFor(user, 1L)).thenReturn(AccessLevel.NONE);
        Map<String, Object> session = new HashMap<>();
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionAttributes(session); connect.setNativeHeader("Authorization", "token");
        connect.setNativeHeader("Accept-Language", "zh-TW"); connect.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()), null);
        assertEquals(principal, connect.getUser());
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setSessionAttributes(session); subscribe.setUser(principal); subscribe.setDestination("/topic/sheets/1");
        subscribe.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders());
        assertEquals("無權存取表單 1", assertThrows(MessagingException.class, () -> interceptor.preSend(message, null)).getMessage());
        subscribe.setNativeHeader("Accept-Language", "zh-CN");
        assertEquals("无权访问表单 1", assertThrows(MessagingException.class, () -> interceptor.preSend(message, null)).getMessage());
    }
}
