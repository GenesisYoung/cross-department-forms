package com.factory.collab.messaging;

import com.factory.collab.dto.Dtos.NotificationDto;

import java.util.List;

/** Notifications to deliver to connected users; fanned out to every instance via RabbitMQ. */
public record UserPush(List<Item> items) {
    public static final String ROUTING_KEY = "user.push";

    public record Item(String username, NotificationDto notification) {}
}
