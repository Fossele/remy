package com.example.messaging.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages",
        indexes = {
                @Index(name = "idx_channel_created_at", columnList = "channel_id, created_at DESC"),
                @Index(name = "idx_sender_id", columnList = "sender_id")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "sender_id")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID senderId;


    @Column(name = "channel_id", updatable = false, nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID channelId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column( name = "created_at",nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Column(name = "is_edited", nullable = false)
    private boolean edited = false;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public ChatMessage(Object o, String username, Instant now) {
    }
}
