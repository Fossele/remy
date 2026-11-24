package com.example.messaging.repository;

import com.example.messaging.entity.FundWithdrawalApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundWithdrawalApprovalRepository extends JpaRepository<FundWithdrawalApproval, UUID> {
    List<FundWithdrawalApproval> findByWithdrawalId(UUID withdrawalId);
    Optional<FundWithdrawalApproval> findByWithdrawalIdAndAdminId(UUID withdrawalId, UUID adminId);
}
