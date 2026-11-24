package com.example.messaging.service;

import com.example.messaging.dto.CommunityContributionResponse;
import com.example.messaging.entity.CommunityContribution;
import com.example.messaging.payment.*;
import com.example.messaging.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityContributionPaymentService {
    private final CommunityFinanceService financeService;
    private final PaymentProvider paymentProvider;
    private final FapshiProperties fapshiProperties;
    private final UserRepository userRepository;

    public CommunityContributionResponse initiate(
            UUID neighborhoodId,
            UUID fundId,
            long amount,
            UUID contributorId
    ) {
        CommunityContribution contribution = financeService.createContribution(
                neighborhoodId, fundId, amount, contributorId);
        String email = userRepository.findById(contributorId)
                .map(user -> user.getEmail())
                .orElse(null);

        PaymentInitiation initiation;
        try {
            initiation = paymentProvider.initiateCollection(new CollectionRequest(
                    amount,
                    email,
                    contributorId.toString(),
                    contribution.getExternalId(),
                    "Community project contribution",
                    blankToNull(fapshiProperties.getRedirectUrl()),
                    blankToNull(fapshiProperties.getWebhookUrl())
            ));
        } catch (RestClientException | IllegalStateException exception) {
            financeService.failPaymentInitiation(contribution.getId(), contributorId);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Fapshi could not initiate the payment", exception);
        }

        CommunityContribution updated = financeService.recordPaymentInitiation(
                contribution.getId(),
                contributorId,
                initiation.providerTransactionId(),
                initiation.paymentUrl()
        );
        return CommunityContributionResponse.fromEntity(updated);
    }

    public void processWebhook(String providedSecret, FapshiWebhookPayload payload) {
        if (!paymentProvider.verifyWebhookSecret(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment webhook secret");
        }
        if (payload == null || payload.transId() == null || payload.externalId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Fapshi webhook payload");
        }

        PaymentStatus verifiedStatus;
        try {
            verifiedStatus = paymentProvider.getPaymentStatus(payload.transId());
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to verify Fapshi payment status", exception);
        }
        if (!payload.transId().equals(verifiedStatus.providerTransactionId())
                || !payload.externalId().equals(verifiedStatus.externalId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook transaction does not match Fapshi status");
        }
        financeService.applyPaymentStatus(verifiedStatus);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record FapshiWebhookPayload(
            String transId,
            String status,
            Long amount,
            Long revenue,
            String externalId
    ) {
    }
}
