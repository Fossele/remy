package com.example.messaging.service;

import com.example.messaging.dto.AuditEventResponse;
import com.example.messaging.entity.*;
import com.example.messaging.repository.AuditEventRepository;
import com.example.messaging.repository.NeighborhoodMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditEventRepository auditEventRepository;
    private final NeighborhoodMembershipRepository membershipRepository;

    @Transactional
    public AuditEvent record(
            UUID actorId,
            AuditAction action,
            AuditOutcome outcome,
            String targetType,
            UUID targetId,
            UUID neighborhoodId,
            String details
    ) {
        String safeDetails = details == null ? null :
                (details.length() <= 500 ? details : details.substring(0, 500));
        return auditEventRepository.save(AuditEvent.builder()
                .actorId(actorId)
                .action(action)
                .outcome(outcome)
                .targetType(targetType)
                .targetId(targetId)
                .neighborhoodId(neighborhoodId)
                .details(safeDetails)
                .build());
    }

    public Page<AuditEventResponse> listNeighborhoodEvents(
            UUID neighborhoodId,
            UUID moderatorId,
            UUID actorId,
            AuditAction action,
            AuditOutcome outcome,
            Pageable pageable
    ) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, moderatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED
                || (membership.getRole() != NeighborhoodMembershipRole.MODERATOR
                && membership.getRole() != NeighborhoodMembershipRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Moderator privileges required");
        }

        record(moderatorId, AuditAction.AUDIT_EVENTS_ACCESSED, AuditOutcome.SUCCESS,
                "NEIGHBORHOOD", neighborhoodId, neighborhoodId, "Moderator viewed audit events");

        return auditEventRepository
                .findNeighborhoodEvents(neighborhoodId, actorId, action, outcome, pageable)
                .map(AuditEventResponse::fromEntity);
    }
}
