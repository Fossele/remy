package com.example.messaging.dto;

import jakarta.validation.constraints.Min;

public record CreateContributionRequest(@Min(100) long amount) {
}
