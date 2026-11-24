package com.example.messaging.repository;

import com.example.messaging.entity.CommunityFund;
import com.example.messaging.entity.CommunityFundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityFundRepository extends JpaRepository<CommunityFund, UUID> {
    List<CommunityFund> findByNeighborhoodIdAndStatusOrderByCreatedAtDesc(
            UUID neighborhoodId, CommunityFundStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fund from CommunityFund fund where fund.id = :id")
    Optional<CommunityFund> findByIdForUpdate(@Param("id") UUID id);
}
