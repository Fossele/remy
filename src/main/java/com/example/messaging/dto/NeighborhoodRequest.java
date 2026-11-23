package com.example.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NeighborhoodRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 120) String location,
        @Size(max = 500) String description
) {
}
