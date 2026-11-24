package com.example.messaging.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFundWithdrawalRequest(
        @Min(100) long amount,
        @NotBlank @Size(max = 500) String purpose,
        @NotBlank @Size(max = 100) String beneficiaryName,
        @NotBlank @Pattern(regexp = "\\d{9}", message = "must be a 9-digit Cameroon mobile number without country code")
        String beneficiaryPhone,
        @Pattern(regexp = "mobile money|orange money", message = "must be 'mobile money' or 'orange money'")
        String beneficiaryMedium
) {
}
