package com.example.messaging.payment;

public record PaymentInitiation(String providerTransactionId, String paymentUrl) {
}
