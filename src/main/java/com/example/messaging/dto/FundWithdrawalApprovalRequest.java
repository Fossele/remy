package com.example.messaging.dto;

import jakarta.validation.constraints.NotNull;

public record FundWithdrawalApprovalRequest(@NotNull Boolean approved) {
}
