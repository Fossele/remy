package com.example.messaging.service;

import com.example.messaging.dto.FundWithdrawalResponse;
import com.example.messaging.payment.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityPayoutService {
    private final CommunityFinanceService financeService;
    private final PayoutProvider payoutProvider;

    public FundWithdrawalResponse initiate(
            UUID neighborhoodId, UUID fundId, UUID withdrawalId, UUID adminId) {
        long amount = financeService.validatePayoutAllowed(
                neighborhoodId, fundId, withdrawalId, adminId);
        if (amount == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrator roster changed; request was cancelled");
        }
        PayoutBalance balance;
        try {
            balance = payoutProvider.getPayoutBalance();
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Fapshi payout service is unavailable", exception);
        }
        if (!"XAF".equalsIgnoreCase(balance.currency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fapshi payout service currency must be XAF");
        }

        if (balance.amount() < amount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Fapshi payout service has insufficient balance");
        }
        PayoutOrder order = financeService.beginPayout(neighborhoodId, fundId, withdrawalId, adminId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal was cancelled because admins changed");
        }

        PayoutInitiation initiation;
        try {
            initiation = payoutProvider.initiatePayout(order.request());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                financeService.failPayoutInitiation(order.payoutAttemptId());
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Fapshi could not confirm payout initiation", exception);
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Fapshi payout initiation outcome is unknown", exception);
        }

        financeService.recordPayoutInitiation(
                order.payoutAttemptId(), initiation.providerTransactionId());
        return financeService.getWithdrawal(neighborhoodId, fundId, withdrawalId, adminId);
    }

    public void processWebhook(
            String providedSecret,
            CommunityContributionPaymentService.FapshiWebhookPayload payload
    ) {
        if (!payoutProvider.verifyPayoutWebhookSecret(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment webhook secret");
        }
        if (payload == null || payload.transId() == null || payload.externalId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Fapshi payout webhook payload");
        }

        PaymentStatus verifiedStatus;
        try {
            verifiedStatus = payoutProvider.getPayoutStatus(payload.transId());
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Unable to verify Fapshi payout status", exception);
        }
        if (!payload.transId().equals(verifiedStatus.providerTransactionId())
                || !payload.externalId().equals(verifiedStatus.externalId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook transaction does not match Fapshi payout");
        }
        financeService.applyPayoutStatus(verifiedStatus);
    }
}
