package com.example.messaging.controller;

import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.dto.MessageEvent;
import com.example.messaging.dto.TypingEventPayload;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;


import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MessageController {

    private final ChatMessageRepository chatMessageRepository;

    @MessageMapping("/chat.updateMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
     public ChatMessageResponse updateMessage(@DestinationVariable UUID channelId, @Payload MessageEvent event){
         ChatMessage chatMessage = chatMessageRepository.findById(event.messageId()).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
         chatMessage.setContent(event.content());
         chatMessageRepository.save(chatMessage);
         return ChatMessageResponse.fromEntity(chatMessage);
     }

    @MessageMapping("/chat.deleteMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
    public ChatMessageResponse deleteMessage(@DestinationVariable UUID channelId, @Payload MessageEvent event){
        ChatMessage chatMessage = chatMessageRepository.findById(event.messageId()).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        chatMessage.setDeleted(true);
        chatMessageRepository.save(chatMessage);
        return ChatMessageResponse.fromEntity(chatMessage);
    }


    @MessageMapping("/chat.sendMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
    public ChatMessageResponse sendMessage(@DestinationVariable UUID channelId, @Payload MessageEvent event){
         ChatMessage chatMessage = ChatMessage.builder()
                 .content(event.content())
                 .channelId(channelId)
                 .senderId(event.senderId())
                 .build();
        chatMessageRepository.save(chatMessage);
        return ChatMessageResponse.fromEntity(chatMessage);
    }

    @MessageMapping("/channel/{channelId}/typing")
    @SendTo("/topic/channel/{channelId}/typing")
    public TypingEventPayload handleTypingEvent(
            @DestinationVariable UUID channelId,
            TypingEventPayload payload) {

        // Return without mutating database records.
        // This is pure low-latency event-forwarding.
        return payload;
    }


}
