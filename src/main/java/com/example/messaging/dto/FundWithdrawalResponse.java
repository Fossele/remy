package com.example.messaging.dto;

import com.example.messaging.entity.FundWithdrawal;

import java.time.Instant;
import java.util.UUID;

public record FundWithdrawalResponse(
        UUID id,
        UUID fundId,
        UUID requestedBy,
        long amount,
        String currency,
        String purpose,
        String beneficiaryName,
        String beneficiaryPhone,
        String beneficiaryMedium,
        String status,
        int requiredApprovals,
        int approvalsReceived,
        String receiptReference,
        String payoutTransactionId,
        String payoutStatus,
        Instant createdAt,
        Instant paidAt
) {
    public static FundWithdrawalResponse fromEntity(
            FundWithdrawal withdrawal,
            String currency,
            int requiredApprovals,
            int approvalsReceived,
            String beneficiaryPhone,
            String payoutTransactionId,
            String payoutStatus
    ) {
        return new FundWithdrawalResponse(
                withdrawal.getId(),
                withdrawal.getFundId(),
                withdrawal.getRequestedBy(),
                withdrawal.getAmount(),
                currency,
                withdrawal.getPurpose(),
                withdrawal.getBeneficiaryName(),
                beneficiaryPhone,
                withdrawal.getBeneficiaryMedium(),
                withdrawal.getStatus().name(),
                requiredApprovals,
                approvalsReceived,
                withdrawal.getReceiptReference(),
                payoutTransactionId,
                payoutStatus,
                withdrawal.getCreatedAt(),
                withdrawal.getPaidAt()
        );
    }
}
