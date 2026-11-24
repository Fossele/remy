package com.example.messaging.repository;

import com.example.messaging.entity.FundWithdrawalPayoutAttempt;
import com.example.messaging.entity.FundWithdrawalPayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundWithdrawalPayoutAttemptRepository
        extends JpaRepository<FundWithdrawalPayoutAttempt, UUID> {
    Optional<FundWithdrawalPayoutAttempt> findByExternalId(String externalId);
    Optional<FundWithdrawalPayoutAttempt> findFirstByWithdrawalIdOrderByCreatedAtDesc(UUID withdrawalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from FundWithdrawalPayoutAttempt attempt where attempt.id = :id")
    Optional<FundWithdrawalPayoutAttempt> findByIdForUpdate(@Param("id") UUID id);

    List<FundWithdrawalPayoutAttempt> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            List<FundWithdrawalPayoutStatus> statuses, Instant createdAt);
}
