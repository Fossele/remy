package com.example.messaging.repository;

import com.example.messaging.entity.CommunityContribution;
import com.example.messaging.entity.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityContributionRepository extends JpaRepository<CommunityContribution, UUID> {
    Optional<CommunityContribution> findByExternalId(String externalId);
    Optional<CommunityContribution> findByProviderTransactionId(String providerTransactionId);
    List<CommunityContribution> findByFundId(UUID fundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contribution from CommunityContribution contribution where contribution.externalId = :externalId")
    Optional<CommunityContribution> findByExternalIdForUpdate(@Param("externalId") String externalId);

    List<CommunityContribution> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ContributionStatus status, Instant createdAt);
}
