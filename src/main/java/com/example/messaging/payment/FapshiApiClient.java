package com.example.messaging.payment;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

@Component
public class FapshiApiClient implements PaymentProvider, PayoutProvider {
    private final RestClient restClient;
    private final FapshiProperties properties;

    public FapshiApiClient(FapshiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public PaymentInitiation initiateCollection(CollectionRequest request) {
        requireCollectionCredentials();
        InitiatePayResponse response = restClient.post()
                .uri("/initiate-pay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("apiuser", properties.getCollectionApiUser())
                .header("apikey", properties.getCollectionApiKey())
                .body(new InitiatePayRequest(
                        request.amount(),
                        request.email(),
                        request.redirectUrl(),
                        request.userId(),
                        request.externalId(),
                        request.message(),
                        request.webhookUrl()
                ))
                .retrieve()
                .body(InitiatePayResponse.class);

        if (response == null || response.transId() == null || response.link() == null) {
            throw new IllegalStateException("Fapshi returned an incomplete payment initiation response");
        }
        return new PaymentInitiation(response.transId(), response.link());
    }

    @Override
    public PaymentStatus getPaymentStatus(String providerTransactionId) {
        requireCollectionCredentials();
        FapshiStatusResponse response = restClient.get()
                .uri("/payment-status/{transId}", providerTransactionId)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("apiuser", properties.getCollectionApiUser())
                .header("apikey", properties.getCollectionApiKey())
                .retrieve()
                .body(FapshiStatusResponse.class);

        if (response == null || response.transId() == null) {
            throw new IllegalStateException("Fapshi returned an incomplete payment status response");
        }
        return new PaymentStatus(
                response.transId(),
                response.externalId(),
                mapStatus(response.status()),
                response.amount(),
                response.revenue()
        );
    }

    @Override
    public boolean verifyWebhookSecret(String providedSecret) {
        return secretsMatch(properties.getWebhookSecret(), providedSecret);
    }

    @Override
    public PayoutBalance getPayoutBalance() {
        requirePayoutCredentials();
        PayoutBalanceResponse response = restClient.get()
                .uri("/balance")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("apiuser", properties.getPayoutApiUser())
                .header("apikey", properties.getPayoutApiKey())
                .retrieve()
                .body(PayoutBalanceResponse.class);
        if (response == null || response.balance() == null || response.currency() == null) {
            throw new IllegalStateException("Fapshi returned an incomplete payout balance response");
        }
        return new PayoutBalance(response.balance(), response.currency());
    }

    @Override
    public PayoutInitiation initiatePayout(PayoutRequest request) {
        requirePayoutCredentials();
        PayoutResponse response = restClient.post()
                .uri("/payout")
                .contentType(MediaType.APPLICATION_JSON)
                .header("apiuser", properties.getPayoutApiUser())
                .header("apikey", properties.getPayoutApiKey())
                .body(new InitiatePayoutRequest(
                        request.amount(),
                        request.phone(),
                        request.medium(),
                        request.name(),
                        null,
                        request.userId(),
                        request.externalId(),
                        request.message()
                ))
                .retrieve()
                .body(PayoutResponse.class);
        if (response == null || response.transId() == null) {
            throw new IllegalStateException("Fapshi returned an incomplete payout response");
        }
        return new PayoutInitiation(response.transId());
    }

    @Override
    public PaymentStatus getPayoutStatus(String providerTransactionId) {
        requirePayoutCredentials();
        return toPaymentStatus(restClient.get()
                .uri("/payment-status/{transId}", providerTransactionId)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("apiuser", properties.getPayoutApiUser())
                .header("apikey", properties.getPayoutApiKey())
                .retrieve()
                .body(FapshiStatusResponse.class));
    }

    @Override
    public Optional<PaymentStatus> findPayoutByExternalId(String externalId) {
        requirePayoutCredentials();
        List<FapshiStatusResponse> responses = restClient.get()
                .uri("/transaction/{externalId}", externalId)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("apiuser", properties.getPayoutApiUser())
                .header("apikey", properties.getPayoutApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (responses == null || responses.isEmpty()) {
            return Optional.empty();
        }
        return responses.stream()
                .filter(response -> externalId.equals(response.externalId()))
                .findFirst()
                .map(this::toPaymentStatus);
    }

    @Override
    public boolean verifyPayoutWebhookSecret(String providedSecret) {
        return secretsMatch(properties.getWebhookSecret(), providedSecret);
    }

    private boolean secretsMatch(String expectedSecret, String providedSecret) {
        return expectedSecret != null
                && !expectedSecret.isBlank()
                && providedSecret != null
                && MessageDigest.isEqual(
                        expectedSecret.getBytes(StandardCharsets.UTF_8),
                        providedSecret.getBytes(StandardCharsets.UTF_8)
                );
    }

    private PaymentStatus toPaymentStatus(FapshiStatusResponse response) {
        if (response == null || response.transId() == null) {
            throw new IllegalStateException("Fapshi returned an incomplete payment status response");
        }
        return new PaymentStatus(
                response.transId(),
                response.externalId(),
                mapStatus(response.status()),
                response.amount(),
                response.revenue()
        );
    }

    private void requireCollectionCredentials() {
        if (properties.getCollectionApiUser() == null || properties.getCollectionApiUser().isBlank()
                || properties.getCollectionApiKey() == null || properties.getCollectionApiKey().isBlank()) {
            throw new IllegalStateException("Fapshi collection credentials are not configured");
        }
    }

    private void requirePayoutCredentials() {
        if (properties.getPayoutApiUser() == null || properties.getPayoutApiUser().isBlank()
                || properties.getPayoutApiKey() == null || properties.getPayoutApiKey().isBlank()) {
            throw new IllegalStateException("Fapshi payout credentials are not configured");
        }
    }

    private String mapStatus(String status) {
        if (status == null) {
            throw new IllegalStateException("Fapshi returned a payment without a status");
        }
        return switch (status.toUpperCase()) {
            case "CREATED", "PENDING" -> "PENDING";
            case "SUCCESSFUL" -> "CONFIRMED";
            case "FAILED" -> "FAILED";
            case "EXPIRED" -> "EXPIRED";
            default -> throw new IllegalStateException("Fapshi returned an unknown payment status");
        };
    }

    private record InitiatePayRequest(
            long amount,
            String email,
            String redirectUrl,
            String userId,
            String externalId,
            String message,
            String webhook
    ) {
    }

    private record InitiatePayResponse(String link, String transId) {
    }

    private record FapshiStatusResponse(
            String transId,
            String status,
            Long amount,
            Long revenue,
            String externalId
    ) {
    }

    private record PayoutBalanceResponse(String service, Long balance, String currency) {
    }

    private record InitiatePayoutRequest(
            long amount,
            String phone,
            String medium,
            String name,
            String email,
            String userId,
            String externalId,
            String message
    ) {
    }

    private record PayoutResponse(String message, String transId) {
    }
}
