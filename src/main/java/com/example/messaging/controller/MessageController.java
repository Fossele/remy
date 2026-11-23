package com.example.messaging.controller;

import com.example.messaging.dto.ChatMessageResponse;
import com.example.messaging.dto.MessageEvent;
import com.example.messaging.dto.TypingEventPayload;
import com.example.messaging.entity.ChatMessage;
import com.example.messaging.repository.ChatMessageRepository;
import com.example.messaging.service.NeighborhoodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;
import com.example.messaging.entity.AuditAction;
import com.example.messaging.entity.AuditOutcome;
import com.example.messaging.service.AuditService;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final ChatMessageRepository chatMessageRepository;
    private final NeighborhoodService neighborhoodService;
    private final AuditService auditService;

    @MessageMapping("/chat.updateMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
     public ChatMessageResponse updateMessage(@DestinationVariable UUID channelId,
                                              @Payload MessageEvent event,
                                              Principal principal){
         UUID userId = currentUserId(principal);
         neighborhoodService.requireChannelAccess(channelId, userId);
         ChatMessage chatMessage = chatMessageRepository.findById(event.messageId()).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
         authorizeMessageMutation(chatMessage, channelId, principal);
         chatMessage.setContent(event.content());
         chatMessage.setEdited(true);
         ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
         auditService.record(userId, AuditAction.MESSAGE_EDITED, AuditOutcome.SUCCESS,
                 "MESSAGE", savedMessage.getId(), null, "Message edited");
         log.info("Message edited channelId={} messageId={} actorId={}",
                 channelId, savedMessage.getId(), userId);
         return ChatMessageResponse.fromEntity(savedMessage);
     }

    @MessageMapping("/chat.deleteMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
    public ChatMessageResponse deleteMessage(@DestinationVariable UUID channelId,
                                              @Payload MessageEvent event,
                                              Principal principal){
        UUID userId = currentUserId(principal);
        neighborhoodService.requireChannelAccess(channelId, userId);
        ChatMessage chatMessage = chatMessageRepository.findById(event.messageId()).orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        authorizeMessageMutation(chatMessage, channelId, principal);
        chatMessage.setDeleted(true);
        chatMessageRepository.save(chatMessage);
        auditService.record(userId, AuditAction.MESSAGE_DELETED, AuditOutcome.SUCCESS,
                "MESSAGE", chatMessage.getId(), null, "Message deleted");
        log.info("Message deleted channelId={} messageId={} actorId={}",
                channelId, chatMessage.getId(), userId);
        return ChatMessageResponse.fromEntity(chatMessage);
    }


    @MessageMapping("/chat.sendMessage/{channelId}")
    @SendTo("/topic/channel.{channelId}")
    public ChatMessageResponse sendMessage(@DestinationVariable UUID channelId,
                                            @Payload MessageEvent event,
                                            Principal principal){
         UUID userId = currentUserId(principal);
         neighborhoodService.requireChannelAccess(channelId, userId);
         ChatMessage chatMessage = ChatMessage.builder()
                 .content(event.content())
                 .channelId(channelId)
                 .senderId(userId)
                 .build();
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        auditService.record(userId, AuditAction.MESSAGE_SENT, AuditOutcome.SUCCESS,
                "MESSAGE", savedMessage.getId(), null, "Message sent");
        log.info("Message sent channelId={} messageId={} actorId={}",
                channelId, savedMessage.getId(), userId);
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

    private void authorizeMessageMutation(ChatMessage message, UUID channelId, Principal principal) {
        if (!channelId.equals(message.getChannelId())
                || !currentUserId(principal).equals(message.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this message");
        }
    }

    private UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
    }

}
