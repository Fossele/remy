
package com.example.messaging.dto;

import com.example.messaging.entity.ChatMessage;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID senderId,
        UUID channelId,
        String content,
        boolean edited,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatMessageResponse fromEntity(ChatMessage entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getSenderId(),
                entity.getChannelId(),
                // Obscure content if soft-deleted
                entity.isDeleted() ? "This message was deleted" : entity.getContent(),
                entity.isEdited(),
                entity.isDeleted(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}