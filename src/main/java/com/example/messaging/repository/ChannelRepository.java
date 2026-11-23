package com.example.messaging.repository;

import com.example.messaging.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {
    List<Channel> findByNeighborhoodId(UUID neighborhoodId);
}
