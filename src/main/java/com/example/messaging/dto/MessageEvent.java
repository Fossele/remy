package com.example.messaging.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MessageEvent(
        UUID messageId,
        UUID channelId,

        UUID senderId,
        MutationType mutationType,
        String content,
        boolean edited,
        boolean deleted,
        Instant updatedAt
) {
    public enum MutationType { EDIT, DELETE, CREATE }

    // Compact Constructor handles fallback when null is passed
    public MessageEvent {
        Objects.requireNonNull(senderId, "senderId cannot be null");
        Objects.requireNonNull(channelId, "channelId cannot be null");
        Objects.requireNonNull(mutationType, "mutationType cannot be null");

        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    // Static Factory Methods
    public static MessageEvent editMessage(UUID messageId, UUID channelId, UUID senderId , String newContent) {
        return new MessageEvent(messageId, channelId, senderId , MutationType.EDIT, newContent, true, false, Instant.now());
    }

    public static MessageEvent deleteMessage(UUID messageId, UUID channelId, UUID senderId) {
        return new MessageEvent(messageId, channelId, senderId, MutationType.DELETE, null, false, true, null);
    }

    public static MessageEvent createMessage(UUID messageId, UUID channelId, UUID senderId, String content) {
        return new MessageEvent(null, channelId, senderId, MutationType.CREATE, content, false, false, null);
    }
}


