package com.example.messaging.service;

import com.example.messaging.entity.CommunityContribution;
import com.example.messaging.payment.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityContributionReconciliationJob {
    private final CommunityFinanceService financeService;
    private final PaymentProvider paymentProvider;

    @Scheduled(fixedDelayString = "${app.finance.reconciliation-delay-ms:300000}")
    public void reconcilePendingContributions() {
        for (CommunityContribution contribution : financeService.findPendingContributions(Instant.now().minusSeconds(300))) {
            if (contribution.getProviderTransactionId() == null) {
                continue;
            }
            try {
                financeService.applyPaymentStatus(
                        paymentProvider.getPaymentStatus(contribution.getProviderTransactionId()));
            } catch (RestClientException | IllegalStateException | ResponseStatusException exception) {
                log.warn("Fapshi reconciliation failed contributionId={} providerTransactionId={}",
                        contribution.getId(), contribution.getProviderTransactionId(), exception);
            }
        }
    }
}
