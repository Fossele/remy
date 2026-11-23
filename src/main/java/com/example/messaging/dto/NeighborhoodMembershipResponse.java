package com.example.messaging.dto;

import com.example.messaging.entity.NeighborhoodMembership;

import java.time.Instant;
import java.util.UUID;

public record NeighborhoodMembershipResponse(
        UUID id,
        UUID neighborhoodId,
        UUID userId,
        String status,
        String role,
        Instant verifiedAt
) {
    public static NeighborhoodMembershipResponse fromEntity(NeighborhoodMembership membership) {
        return new NeighborhoodMembershipResponse(
                membership.getId(),
                membership.getNeighborhoodId(),
                membership.getUserId(),
                membership.getStatus().name(),
                membership.getRole().name(),
                membership.getVerifiedAt()
        );
    }
}
