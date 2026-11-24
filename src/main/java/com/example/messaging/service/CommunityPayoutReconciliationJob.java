package com.example.messaging.service;

import com.example.messaging.entity.FundWithdrawalPayoutAttempt;
import com.example.messaging.entity.FundWithdrawalPayoutStatus;
import com.example.messaging.payment.PaymentStatus;
import com.example.messaging.payment.PayoutProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityPayoutReconciliationJob {
    private final CommunityFinanceService financeService;
    private final PayoutProvider payoutProvider;

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-delay-ms:300000}")
    public void reconcilePayouts() {
        for (FundWithdrawalPayoutAttempt attempt : financeService.findPendingPayoutAttempts(
                Instant.now().minusSeconds(60))) {
            try {
                if (attempt.getStatus() == FundWithdrawalPayoutStatus.INITIATING
                        && attempt.getProviderTransactionId() == null) {
                    Optional<PaymentStatus> byExternalId =
                            payoutProvider.findPayoutByExternalId(attempt.getExternalId());
                    if (byExternalId.isEmpty()) {
                        continue;
                    }
                    PaymentStatus status = byExternalId.get();
                    financeService.recordPayoutInitiation(attempt.getId(), status.providerTransactionId());
                    financeService.applyPayoutStatus(status);
                } else if (attempt.getProviderTransactionId() != null) {
                    financeService.applyPayoutStatus(
                            payoutProvider.getPayoutStatus(attempt.getProviderTransactionId()));
                }
            } catch (RestClientException | IllegalStateException | ResponseStatusException exception) {
                log.warn("Fapshi payout reconciliation failed attemptId={} externalId={}",
                        attempt.getId(), attempt.getExternalId(), exception);
            }
        }
    }
}
