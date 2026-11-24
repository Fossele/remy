package com.example.messaging.payment;

public record PaymentStatus(
        String providerTransactionId,
        String externalId,
        String status,
        Long amount,
        Long netAmount
) {
}
