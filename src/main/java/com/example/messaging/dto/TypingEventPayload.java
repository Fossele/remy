package com.example.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingEventPayload {
    private String username;
    private boolean isTyping; // true if typing, false if stopped
}