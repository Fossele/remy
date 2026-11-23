package com.example.messaging.service;

import com.example.messaging.dto.*;
import com.example.messaging.entity.*;
import com.example.messaging.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NeighborhoodService {
    private final NeighborhoodRepository neighborhoodRepository;
    private final NeighborhoodMembershipRepository membershipRepository;
    private final ChannelRepository channelRepository;
    private final MessageReportRepository reportRepository;

    @Transactional
    public NeighborhoodResponse createNeighborhood(NeighborhoodRequest request, UUID userId) {
        Neighborhood neighborhood = neighborhoodRepository.save(Neighborhood.builder()
                .name(request.name())
                .location(request.location())
                .description(request.description())
                .active(true)
                .build());
        membershipRepository.save(NeighborhoodMembership.builder()
                .neighborhoodId(neighborhood.getId())
                .userId(userId)
                .status(NeighborhoodMembershipStatus.VERIFIED)
                .role(NeighborhoodMembershipRole.ADMIN)
                .verifiedAt(Instant.now())
                .build());
        return NeighborhoodResponse.fromEntity(neighborhood);
    }

    public List<NeighborhoodResponse> listNeighborhoods() {
        return neighborhoodRepository.findAll().stream()
                .filter(Neighborhood::isActive)
                .map(NeighborhoodResponse::fromEntity)
                .toList();
    }

    @Transactional
    public NeighborhoodMembershipResponse joinNeighborhood(UUID neighborhoodId, UUID userId) {
        Neighborhood neighborhood = neighborhoodRepository.findById(neighborhoodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Neighborhood not found"));
        if (!neighborhood.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Neighborhood is not active");
        }
        if (membershipRepository.findByNeighborhoodIdAndUserId(neighborhoodId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership already exists");
        }
        NeighborhoodMembership membership = membershipRepository.save(NeighborhoodMembership.builder()
                .neighborhoodId(neighborhoodId)
                .userId(userId)
                .status(NeighborhoodMembershipStatus.PENDING)
                .role(NeighborhoodMembershipRole.MEMBER)
                .build());
        return NeighborhoodMembershipResponse.fromEntity(membership);
    }

    @Transactional
    public NeighborhoodMembershipResponse verifyMember(UUID neighborhoodId, UUID memberId, UUID moderatorId) {
        requireModerator(neighborhoodId, moderatorId);
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));
        membership.setStatus(NeighborhoodMembershipStatus.VERIFIED);
        membership.setVerifiedAt(Instant.now());
        return NeighborhoodMembershipResponse.fromEntity(membershipRepository.save(membership));
    }

    @Transactional
    public Channel createChannel(UUID neighborhoodId, NeighborhoodChannelRequest request, UUID userId) {
        requireModerator(neighborhoodId, userId);
        return channelRepository.save(Channel.builder()
                .name(request.name())
                .description(request.description())
                .createdBy(userId)
                .type(ChannelType.PUBLIC)
                .neighborhoodId(neighborhoodId)
                .category(request.category() == null ? NeighborhoodChannelCategory.GENERAL : request.category())
                .build());
    }

    public List<Channel> listChannels(UUID neighborhoodId, UUID userId) {
        requireVerifiedMember(neighborhoodId, userId);
        return channelRepository.findByNeighborhoodId(neighborhoodId);
    }

    @Transactional
    public MessageReport reportMessage(MessageReportRequest request, UUID reporterId) {
        if (request.messageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageId is required");
        }
        return reportRepository.save(MessageReport.builder()
                .messageId(request.messageId())
                .reporterId(reporterId)
                .reason(request.reason())
                .details(request.details())
                .status(MessageReportStatus.OPEN)
                .build());
    }

    public void requireVerifiedMember(UUID neighborhoodId, UUID userId) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership is not verified");
        }
    }

    public void requireChannelAccess(UUID channelId, UUID userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
        if (channel.getNeighborhoodId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel is not assigned to a neighborhood");
        }
        requireVerifiedMember(channel.getNeighborhoodId(), userId);
    }

    private void requireModerator(UUID neighborhoodId, UUID userId) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED
                || (membership.getRole() != NeighborhoodMembershipRole.MODERATOR
                && membership.getRole() != NeighborhoodMembershipRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Moderator privileges required");
        }
    }
}
