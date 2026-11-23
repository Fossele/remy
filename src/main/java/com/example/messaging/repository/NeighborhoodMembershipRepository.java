package com.example.messaging.repository;

import com.example.messaging.entity.NeighborhoodMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NeighborhoodMembershipRepository extends JpaRepository<NeighborhoodMembership, UUID> {
    Optional<NeighborhoodMembership> findByNeighborhoodIdAndUserId(UUID neighborhoodId, UUID userId);
    List<NeighborhoodMembership> findByNeighborhoodId(UUID neighborhoodId);
    List<NeighborhoodMembership> findByUserId(UUID userId);
}
