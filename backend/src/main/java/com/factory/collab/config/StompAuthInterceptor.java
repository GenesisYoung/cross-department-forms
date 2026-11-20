package com.factory.collab.config;

import com.factory.collab.i18n.Messages;
import com.factory.collab.domain.AccessLevel;
import com.factory.collab.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authenticates STOMP CONNECT frames with the JWT and authorises sheet topic subscriptions. */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern SHEET_TOPIC = Pattern.compile("^/topic/sheets/(\\d+)$");

    private final TokenAuthenticator authenticator;
    private final PermissionService permissionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;

        String language = acc.getFirstNativeHeader("Accept-Language");
        if (language == null && acc.getSessionAttributes() != null)
            language = (String) acc.getSessionAttributes().get("collab.language");
        java.util.Locale locale = Messages.resolve(language);
        if (StompCommand.CONNECT.equals(acc.getCommand())) {
            if (acc.getSessionAttributes() != null) acc.getSessionAttributes().put("collab.language", locale.toLanguageTag());
            String header = acc.getFirstNativeHeader("Authorization");
            UsernamePasswordAuthenticationToken auth = authenticator.authenticate(header)
                    .orElseThrow(() -> new MessagingException(Messages.text(locale, "error.unauthorized")));
            acc.setUser(auth);
        } else if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
            if (!(acc.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                    || !(auth.getPrincipal() instanceof AuthUser user)) {
                throw new MessagingException(Messages.text(locale, "error.unauthorized"));
            }
            String dest = acc.getDestination() == null ? "" : acc.getDestination();
            Matcher m = SHEET_TOPIC.matcher(dest);
            if (m.matches()) {
                long sheetId = Long.parseLong(m.group(1));
                if (!permissionService.levelFor(user, sheetId).atLeast(AccessLevel.VIEW)) {
                    throw new MessagingException(Messages.text(locale, "error.noSheetAccess", sheetId));
                }
            } else if (!dest.startsWith("/user/queue/") && !dest.equals("/topic/activity")) {
                throw new MessagingException(Messages.text(locale, "error.destination"));
            }
        }
        return message;
    }
}
