package com.example.messaging.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_neighborhood_created", columnList = "neighborhood_id, created_at"),
                @Index(name = "idx_audit_actor_created", columnList = "actor_id, created_at"),
                @Index(name = "idx_audit_action_created", columnList = "action, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "actor_id")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    @Column(name = "target_id")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID targetId;

    @Column(name = "neighborhood_id")
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID neighborhoodId;

    @Column(length = 500)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PreUpdate
    @PreRemove
    private void preventMutation() {
        throw new UnsupportedOperationException("Audit events are append-only");
    }
}
