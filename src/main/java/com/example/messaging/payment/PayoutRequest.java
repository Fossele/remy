package com.example.messaging.payment;

public record PayoutRequest(
        long amount,
        String phone,
        String medium,
        String name,
        String userId,
        String externalId,
        String message
) {
}
