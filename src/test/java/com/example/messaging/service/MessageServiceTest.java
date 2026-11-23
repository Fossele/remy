package com.example.messaging.service;

import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private ChatMessageRepository repository;

    @InjectMocks
    private MessageService messageService;

    @Test
    void getMessagesQueriesRepositoryAndMapsEntities() {
        UUID channelId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-02T00:00:00Z");
        PageRequest pageable = PageRequest.of(0, 50);
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .channelId(channelId)
                .senderId(UUID.randomUUID())
                .content("hello")
                .deleted(false)
                .build();
        Page<ChatMessage> page = new PageImpl<>(List.of(message), pageable, 1);
        when(repository.findByChannelIdAndCreatedAtBetween(channelId, start, end, pageable))
                .thenReturn(page);

        Page<ChatMessageResponse> response = messageService.getMessages(channelId, start, end, pageable);

        verify(repository).findByChannelIdAndCreatedAtBetween(channelId, start, end, pageable);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().content()).isEqualTo("hello");
    }
}
