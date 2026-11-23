package com.example.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MessageReportRequest(
        UUID messageId,
        @NotBlank @Size(max = 40) String reason,
        @Size(max = 500) String details
) {
}
