package com.example.messaging.dto;

import com.example.messaging.entity.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID actorId,
        String action,
        String outcome,
        String targetType,
        UUID targetId,
        UUID neighborhoodId,
        String details,
        Instant createdAt
) {
    public static AuditEventResponse fromEntity(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getActorId(),
                event.getAction().name(),
                event.getOutcome().name(),
                event.getTargetType(),
                event.getTargetId(),
                event.getNeighborhoodId(),
                event.getDetails(),
                event.getCreatedAt()
        );
    }
}
