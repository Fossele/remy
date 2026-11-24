package com.example.messaging.dto;

import com.example.messaging.entity.CommunityFund;

import java.time.Instant;
import java.util.UUID;

public record CommunityFundResponse(
        UUID id,
        UUID neighborhoodId,
        String name,
        String description,
        Long targetAmount,
        long contributedAmount,
        long availableAmount,
        long reservedAmount,
        String currency,
        String status,
        UUID createdBy,
        Instant createdAt
) {
    public static CommunityFundResponse fromEntity(
            CommunityFund fund,
            long contributedAmount,
            long availableAmount,
            long reservedAmount
    ) {
        return new CommunityFundResponse(
                fund.getId(),
                fund.getNeighborhoodId(),
                fund.getName(),
                fund.getDescription(),
                fund.getTargetAmount(),
                contributedAmount,
                availableAmount,
                reservedAmount,
                fund.getCurrency(),
                fund.getStatus().name(),
                fund.getCreatedBy(),
                fund.getCreatedAt()
        );
    }
}
