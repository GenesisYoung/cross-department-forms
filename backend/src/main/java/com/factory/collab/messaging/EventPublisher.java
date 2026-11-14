package com.factory.collab.messaging;

import com.factory.collab.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Services call {@link #publish}; the message only reaches RabbitMQ once the database transaction has
 * committed, so subscribers never see changes that were rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final ApplicationEventPublisher springEvents;
    private final RabbitTemplate rabbitTemplate;

    public void publish(SheetEvent event) {
        springEvents.publishEvent(BusMessage.of(event));
    }

    public void publish(UserPush push) {
        if (push.items().isEmpty()) return;
        springEvents.publishEvent(BusMessage.of(push));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void afterCommit(BusMessage message) {
        String routingKey = message.sheetEvent() != null ? message.sheetEvent().routingKey() : UserPush.ROUTING_KEY;
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, message);
        } catch (AmqpException e) {
            // Data is already committed; clients will catch up on their next load / version check.
            log.error("Failed to publish {} to RabbitMQ: {}", routingKey, e.getMessage());
        }
    }
}
