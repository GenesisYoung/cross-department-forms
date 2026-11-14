package com.factory.collab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology:
 * <pre>
 *  collab.events (topic) --sheet.#--> collab.notifications (durable, shared)  -> persist notifications
 *                        --#-------> collab.broadcast.&lt;random&gt; (one per instance) -> push to WebSocket clients
 *  collab.events.dlx (fanout) -> collab.notifications.dlq  (poison messages after retries)
 * </pre>
 */
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "collab.events";
    public static final String DLX = "collab.events.dlx";
    public static final String NOTIFICATION_QUEUE = "collab.notifications";
    public static final String NOTIFICATION_DLQ = "collab.notifications.dlq";

    @Bean
    public TopicExchange collabExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).deadLetterExchange(DLX).build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(notificationQueue).to(collabExchange).with("sheet.#");
    }

    @Bean
    public Binding dlqBinding(Queue notificationDlq, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDlq).to(deadLetterExchange);
    }

    /** Exclusive, auto-delete queue: every running instance receives every event. */
    @Bean
    public Queue broadcastQueue() {
        return new AnonymousQueue(new Base64UrlNamingStrategy("collab.broadcast."));
    }

    @Bean
    public Binding broadcastBinding(Queue broadcastQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(broadcastQueue).to(collabExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
