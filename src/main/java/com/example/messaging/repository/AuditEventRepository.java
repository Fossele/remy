package com.example.messaging.repository;

import com.example.messaging.entity.AuditAction;
import com.example.messaging.entity.AuditEvent;
import com.example.messaging.entity.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    @Query("""
            SELECT event FROM AuditEvent event
            WHERE event.neighborhoodId = :neighborhoodId
              AND (:actorId IS NULL OR event.actorId = :actorId)
              AND (:action IS NULL OR event.action = :action)
              AND (:outcome IS NULL OR event.outcome = :outcome)
            """)
    Page<AuditEvent> findNeighborhoodEvents(
            @Param("neighborhoodId") UUID neighborhoodId,
            @Param("actorId") UUID actorId,
            @Param("action") AuditAction action,
            @Param("outcome") AuditOutcome outcome,
            Pageable pageable
    );
}
