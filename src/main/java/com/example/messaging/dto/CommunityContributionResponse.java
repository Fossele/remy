package com.example.messaging.dto;

import com.example.messaging.entity.CommunityContribution;

import java.time.Instant;
import java.util.UUID;

public record CommunityContributionResponse(
        UUID id,
        UUID fundId,
        long amount,
        long netAmount,
        String currency,
        String status,
        String paymentLink,
        String externalId,
        Instant createdAt
) {
    public static CommunityContributionResponse fromEntity(CommunityContribution contribution) {
        return new CommunityContributionResponse(
                contribution.getId(),
                contribution.getFundId(),
                contribution.getAmount(),
                contribution.getNetAmount(),
                contribution.getCurrency(),
                contribution.getStatus().name(),
                contribution.getPaymentLink(),
                contribution.getExternalId(),
                contribution.getCreatedAt()
        );
    }
}
