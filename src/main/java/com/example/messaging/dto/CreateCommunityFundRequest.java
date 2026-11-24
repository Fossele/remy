package com.example.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCommunityFundRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Positive Long targetAmount
) {
}
