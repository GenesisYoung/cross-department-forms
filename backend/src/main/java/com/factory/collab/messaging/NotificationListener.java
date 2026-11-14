package com.factory.collab.messaging;

import com.factory.collab.config.RabbitConfig;
import com.factory.collab.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Single consumer group (shared durable queue): each event creates notifications exactly once. */
@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onMessage(BusMessage message) {
        if (message.sheetEvent() != null) {
            notificationService.handle(message.sheetEvent());
        }
    }
}
