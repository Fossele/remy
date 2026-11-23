package com.example.messaging.controller;

import com.example.messaging.dto.MessageEvent;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import com.example.messaging.service.NeighborhoodService;
import com.example.messaging.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private ChatMessageRepository repository;

    @Mock
    private Principal principal;

    @Mock
    private NeighborhoodService neighborhoodService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MessageController controller;

    @Test
    void sendMessageUsesAuthenticatedUserInsteadOfPayloadSender() {
        UUID authenticatedUser = UUID.randomUUID();
        UUID spoofedUser = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        when(principal.getName()).thenReturn(authenticatedUser.toString());
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.sendMessage(channelId,
                new MessageEvent(null, channelId, spoofedUser, MessageEvent.MutationType.CREATE,
                        "hello", false, false, null),
                principal);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSenderId()).isEqualTo(authenticatedUser);
    }

    @Test
    void updateRejectsMessageOwnedByAnotherUser() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .channelId(channelId)
                .senderId(owner)
                .content("original")
                .build();
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        when(principal.getName()).thenReturn(attacker.toString());

        assertThatThrownBy(() -> controller.updateMessage(
                channelId,
                new MessageEvent(message.getId(), channelId, attacker, MessageEvent.MutationType.EDIT,
                        "changed", true, false, null),
                principal
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);

        verify(repository, never()).save(any());
    }

    @Test
    void deleteMarksOwnedMessageDeleted() {
        UUID owner = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .channelId(channelId)
                .senderId(owner)
                .content("hello")
                .build();
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        when(principal.getName()).thenReturn(owner.toString());

        com.example.messaging.dto.ChatMessageResponse response = controller.deleteMessage(
                channelId,
                new MessageEvent(message.getId(), channelId, owner, MessageEvent.MutationType.DELETE,
                        null, false, true, null),
                principal
        );

        assertThat(message.isDeleted()).isTrue();
        assertThat(response.deleted()).isTrue();
        verify(repository).save(message);
    }

    @Test
    void updateRejectsMessageFromAnotherChannel() {
        UUID owner = UUID.randomUUID();
        UUID messageChannel = UUID.randomUUID();
        UUID requestChannel = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .channelId(messageChannel)
                .senderId(owner)
                .content("hello")
                .build();
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        when(principal.getName()).thenReturn(owner.toString());

        assertThatThrownBy(() -> controller.updateMessage(
                requestChannel,
                new MessageEvent(message.getId(), requestChannel, owner, MessageEvent.MutationType.EDIT,
                        "changed", true, false, null),
                principal
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsUnknownMessage() {
        UUID messageId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        when(repository.findById(messageId)).thenReturn(Optional.empty());
        when(principal.getName()).thenReturn(UUID.randomUUID().toString());

        assertThatThrownBy(() -> controller.updateMessage(
                channelId,
                new MessageEvent(messageId, channelId, UUID.randomUUID(), MessageEvent.MutationType.EDIT,
                        "changed", true, false, null),
                principal
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);

        verify(repository, never()).save(any());
    }

    @Test
    void invalidPrincipalIsRejectedAsUnauthorized() {
        UUID channelId = UUID.randomUUID();
        when(principal.getName()).thenReturn("not-a-uuid");

        assertThatThrownBy(() -> controller.sendMessage(
                channelId,
                new MessageEvent(null, channelId, null, MessageEvent.MutationType.CREATE,
                        "hello", false, false, null),
                principal
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(401);

        verify(repository, never()).save(any());
    }
}
