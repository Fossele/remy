package com.example.messaging.repository;

import com.example.messaging.entity.FundWithdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundWithdrawalRepository extends JpaRepository<FundWithdrawal, UUID> {
    List<FundWithdrawal> findByFundIdOrderByCreatedAtDesc(UUID fundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select withdrawal from FundWithdrawal withdrawal where withdrawal.id = :id")
    Optional<FundWithdrawal> findByIdForUpdate(@Param("id") UUID id);
}
