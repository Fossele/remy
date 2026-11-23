package com.example.messaging.controller;

import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.service.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageHistoryControllerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageHistoryController controller;

    @Test
    void getMessagesPassesTimeRangeAndPageableToService() {
        UUID channelId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-02T00:00:00Z");
        Page<ChatMessageResponse> page = new PageImpl<>(List.of());
        when(messageService.getMessages(eq(channelId), eq(start), eq(end), any(Pageable.class)))
                .thenReturn(page);

        Page<ChatMessageResponse> result =
                controller.getMessagesFromChannel(channelId, start, end, Pageable.ofSize(50));

        assertThat(result).isSameAs(page);
        verify(messageService).getMessages(eq(channelId), eq(start), eq(end), any(Pageable.class));
    }
}
