package com.example.messaging.payment;

public interface PaymentProvider {
    PaymentInitiation initiateCollection(CollectionRequest request);

    PaymentStatus getPaymentStatus(String providerTransactionId);

    boolean verifyWebhookSecret(String providedSecret);
}
