package com.example.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteFundWithdrawalRequest(
        @NotBlank @Size(max = 150) String receiptReference
) {
}
