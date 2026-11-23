package com.example.messaging.controller;

import com.example.messaging.dto.*;
import com.example.messaging.entity.Channel;
import com.example.messaging.entity.MessageReport;
import com.example.messaging.service.NeighborhoodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/neighborhoods")
public class NeighborhoodController {
    private final NeighborhoodService neighborhoodService;

    @GetMapping
    public List<NeighborhoodResponse> listNeighborhoods() {
        return neighborhoodService.listNeighborhoods();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NeighborhoodResponse createNeighborhood(
            @Valid @RequestBody NeighborhoodRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.createNeighborhood(request, currentUserId(jwt));
    }

    @PostMapping("/{neighborhoodId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public NeighborhoodMembershipResponse join(
            @PathVariable UUID neighborhoodId,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.joinNeighborhood(neighborhoodId, currentUserId(jwt));
    }

    @PostMapping("/{neighborhoodId}/members/{memberId}/verify")
    public NeighborhoodMembershipResponse verify(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.verifyMember(neighborhoodId, memberId, currentUserId(jwt));
    }

    @PostMapping("/{neighborhoodId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public Channel createChannel(
            @PathVariable UUID neighborhoodId,
            @Valid @RequestBody NeighborhoodChannelRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.createChannel(neighborhoodId, request, currentUserId(jwt));
    }

    @GetMapping("/{neighborhoodId}/channels")
    public List<Channel> listChannels(
            @PathVariable UUID neighborhoodId,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.listChannels(neighborhoodId, currentUserId(jwt));
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageReport reportMessage(
            @Valid @RequestBody MessageReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return neighborhoodService.reportMessage(request, currentUserId(jwt));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
