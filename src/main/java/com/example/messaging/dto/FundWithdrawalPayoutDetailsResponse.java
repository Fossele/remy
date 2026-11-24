package com.example.messaging.dto;

import com.example.messaging.entity.FundWithdrawal;

import java.util.UUID;

public record FundWithdrawalPayoutDetailsResponse(
        UUID withdrawalId,
        String beneficiaryName,
        String beneficiaryPhone,
        String beneficiaryMedium
) {
    public static FundWithdrawalPayoutDetailsResponse fromEntity(FundWithdrawal withdrawal) {
        return new FundWithdrawalPayoutDetailsResponse(
                withdrawal.getId(),
                withdrawal.getBeneficiaryName(),
                withdrawal.getBeneficiaryPhone(),
                withdrawal.getBeneficiaryMedium()
        );
    }
}
