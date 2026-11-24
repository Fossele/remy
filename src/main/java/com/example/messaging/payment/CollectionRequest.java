package com.example.messaging.payment;

public record CollectionRequest(
        long amount,
        String email,
        String userId,
        String externalId,
        String message,
        String redirectUrl,
        String webhookUrl
) {
}
