package com.example.messaging.service;

import com.example.messaging.dto.NeighborhoodChannelRequest;
import com.example.messaging.dto.NeighborhoodRequest;
import com.example.messaging.dto.MessageReportRequest;
import com.example.messaging.entity.*;
import com.example.messaging.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeighborhoodServiceTest {

    @Mock
    private NeighborhoodRepository neighborhoodRepository;
    @Mock
    private NeighborhoodMembershipRepository membershipRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private MessageReportRepository reportRepository;

    @InjectMocks
    private NeighborhoodService service;

    @Test
    void createNeighborhoodCreatesVerifiedAdminMembership() {
        UUID userId = UUID.randomUUID();
        Neighborhood neighborhood = Neighborhood.builder()
                .id(UUID.randomUUID())
                .name("Maple Grove")
                .location("North district")
                .active(true)
                .build();
        when(neighborhoodRepository.save(any(Neighborhood.class))).thenReturn(neighborhood);
        when(membershipRepository.save(any(NeighborhoodMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createNeighborhood(
                new NeighborhoodRequest("Maple Grove", "North district", "Local residents"),
                userId
        );

        org.mockito.ArgumentCaptor<NeighborhoodMembership> captor =
                org.mockito.ArgumentCaptor.forClass(NeighborhoodMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NeighborhoodMembershipStatus.VERIFIED);
        assertThat(captor.getValue().getRole()).isEqualTo(NeighborhoodMembershipRole.ADMIN);
        assertThat(captor.getValue().getVerifiedAt()).isNotNull();
    }

    @Test
    void joinNeighborhoodCreatesPendingMember() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(neighborhoodRepository.findById(neighborhoodId))
                .thenReturn(Optional.of(Neighborhood.builder()
                        .id(neighborhoodId)
                        .name("Maple Grove")
                        .location("North")
                        .active(true)
                        .build()));
        when(membershipRepository.findByNeighborhoodIdAndUserId(neighborhoodId, userId))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(NeighborhoodMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var membership = service.joinNeighborhood(neighborhoodId, userId);

        assertThat(membership.status()).isEqualTo("PENDING");
        assertThat(membership.role()).isEqualTo("MEMBER");
    }

    @Test
    void unverifiedMemberCannotListChannels() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByNeighborhoodIdAndUserId(neighborhoodId, userId))
                .thenReturn(Optional.of(NeighborhoodMembership.builder()
                        .neighborhoodId(neighborhoodId)
                        .userId(userId)
                        .status(NeighborhoodMembershipStatus.PENDING)
                        .build()));

        assertThatThrownBy(() -> service.listChannels(neighborhoodId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void regularMemberCannotCreateChannel() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByNeighborhoodIdAndUserId(neighborhoodId, userId))
                .thenReturn(Optional.of(NeighborhoodMembership.builder()
                        .neighborhoodId(neighborhoodId)
                        .userId(userId)
                        .status(NeighborhoodMembershipStatus.VERIFIED)
                        .role(NeighborhoodMembershipRole.MEMBER)
                        .build()));

        assertThatThrownBy(() -> service.createChannel(
                neighborhoodId,
                new NeighborhoodChannelRequest("Safety", "Safety alerts", NeighborhoodChannelCategory.SAFETY),
                userId
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void reportMessageRequiresMessageIdAndStoresReport() {
        UUID reporterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(reportRepository.save(any(MessageReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MessageReport report = service.reportMessage(
                new MessageReportRequest(messageId, "HARASSMENT", "Repeated unwanted contact"),
                reporterId
        );

        assertThat(report.getReporterId()).isEqualTo(reporterId);
        assertThat(report.getMessageId()).isEqualTo(messageId);
        assertThat(report.getStatus()).isEqualTo(MessageReportStatus.OPEN);

        assertThatThrownBy(() -> service.reportMessage(
                new MessageReportRequest(null, "SPAM", null),
                reporterId
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
    }
}
