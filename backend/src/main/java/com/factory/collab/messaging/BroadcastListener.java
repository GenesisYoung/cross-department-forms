package com.factory.collab.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Relays bus messages to this instance's WebSocket clients. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastListener {
    private final SimpMessagingTemplate ws;

    @RabbitListener(queues = "#{broadcastQueue.name}")
    public void onMessage(BusMessage message) {
        if (message.sheetEvent() != null) {
            SheetEvent e = message.sheetEvent();
            ws.convertAndSend("/topic/sheets/" + e.sheetId(), e);
            // Lightweight, content-free ping so dashboards can refresh the affected panel.
            ws.convertAndSend("/topic/activity", Map.of(
                    "type", e.type().name(), "sheetId", e.sheetId(), "version", e.version()));
        }
        if (message.userPush() != null) {
            for (UserPush.Item item : message.userPush().items()) {
                ws.convertAndSendToUser(item.username(), "/queue/notifications", item.notification());
            }
        }
    }
}
