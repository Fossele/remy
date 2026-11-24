package com.example.messaging.payment;

import java.util.Optional;

public interface PayoutProvider {
    PayoutBalance getPayoutBalance();

    PayoutInitiation initiatePayout(PayoutRequest request);

    PaymentStatus getPayoutStatus(String providerTransactionId);

    Optional<PaymentStatus> findPayoutByExternalId(String externalId);

    boolean verifyPayoutWebhookSecret(String providedSecret);
}
