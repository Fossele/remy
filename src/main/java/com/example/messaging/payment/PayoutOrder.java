package com.example.messaging.payment;

import java.util.UUID;

public record PayoutOrder(UUID payoutAttemptId, PayoutRequest request) {
}
