package com.example.messaging.dto;

import com.example.messaging.entity.NeighborhoodChannelCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NeighborhoodChannelRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        NeighborhoodChannelCategory category
) {
}
