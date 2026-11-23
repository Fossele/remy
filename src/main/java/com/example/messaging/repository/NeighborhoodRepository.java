package com.example.messaging.repository;

import com.example.messaging.entity.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NeighborhoodRepository extends JpaRepository<Neighborhood, UUID> {
}
