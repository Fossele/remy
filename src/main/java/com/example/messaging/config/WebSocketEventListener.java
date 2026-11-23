package com.example.messaging.config;


import com.example.messaging.dto.TypingEventPayload;
import com.example.messaging.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final SimpMessagingTemplate messaging;

    public WebSocketEventListener(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        logger.info("New WebSocket connection established");
    }


    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = (String) accessor.getSessionAttributes().get("username");
        if (username == null) {
            return;
        }
        logger.info("{} disconnected", username);
        messaging.convertAndSend("/topic/public",
                new ChatMessage( null, username, Instant.now()));

        String activeChannelId = (String) accessor.getSessionAttributes().get("activeChannelId");

        if (username != null && activeChannelId != null) {
            logger.info("User {} disconnected abruptly from channel {}", username, activeChannelId);

            // Create a stop-typing payload
            TypingEventPayload typingEvent = new TypingEventPayload(username, false);

            // Broadcast stop-typing to the channel so active receivers clear immediately
            messaging.convertAndSend(
                    "/topic/channel." + activeChannelId + ".typing",
                    typingEvent
            );
        }
    }
}