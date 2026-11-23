package com.example.messaging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID messageId;

    @Column(name = "reporter_id", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID reporterId;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(length = 500)
    private String details;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageReportStatus status = MessageReportStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
