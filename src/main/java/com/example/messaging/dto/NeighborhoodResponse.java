package com.example.messaging.dto;

import com.example.messaging.entity.Neighborhood;

import java.util.UUID;

public record NeighborhoodResponse(
        UUID id,
        String name,
        String location,
        String description,
        boolean active
) {
    public static NeighborhoodResponse fromEntity(Neighborhood neighborhood) {
        return new NeighborhoodResponse(
                neighborhood.getId(),
                neighborhood.getName(),
                neighborhood.getLocation(),
                neighborhood.getDescription(),
                neighborhood.isActive()
        );
    }
}
