package com.example.messaging.service;

import com.example.messaging.payment.PayoutBalance;
import com.example.messaging.payment.PayoutInitiation;
import com.example.messaging.payment.PayoutOrder;
import com.example.messaging.payment.PayoutProvider;
import com.example.messaging.payment.PayoutRequest;
import com.example.messaging.dto.FundWithdrawalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPayoutServiceTest {

    @Mock
    private CommunityFinanceService financeService;
    @Mock
    private PayoutProvider payoutProvider;

    @InjectMocks
    private CommunityPayoutService payoutService;

    @Test
    void insufficientPayoutServiceBalanceDoesNotInitiatePayout() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID fundId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(financeService.validatePayoutAllowed(neighborhoodId, fundId, withdrawalId, adminId))
                .thenReturn(25_000L);
        when(payoutProvider.getPayoutBalance()).thenReturn(new PayoutBalance(10_000, "XAF"));

        assertThatThrownBy(() -> payoutService.initiate(
                neighborhoodId, fundId, withdrawalId, adminId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("insufficient balance");

        verify(financeService, never()).beginPayout(neighborhoodId, fundId, withdrawalId, adminId);
        verify(payoutProvider, never()).initiatePayout(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void approvedWithdrawalIsSentThroughPayoutProviderAndTracked() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID fundId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        PayoutRequest request = new PayoutRequest(
                25_000, "670000000", "mobile money", "Roadworks contractor",
                adminId.toString(), "payout_123", "Road repair");
        when(financeService.validatePayoutAllowed(neighborhoodId, fundId, withdrawalId, adminId))
                .thenReturn(25_000L);
        when(payoutProvider.getPayoutBalance()).thenReturn(new PayoutBalance(100_000, "XAF"));
        when(financeService.beginPayout(neighborhoodId, fundId, withdrawalId, adminId))
                .thenReturn(new PayoutOrder(attemptId, request));
        when(payoutProvider.initiatePayout(request)).thenReturn(new PayoutInitiation("fapshi-456"));
        when(financeService.getWithdrawal(neighborhoodId, fundId, withdrawalId, adminId))
                .thenReturn(new FundWithdrawalResponse(
                        withdrawalId, fundId, UUID.randomUUID(), 25_000, "XAF", "Road repair",
                        "Roadworks contractor", "670000000", "mobile money", "PAYOUT_PENDING",
                        2, 2, null, "fapshi-456", "PENDING", null, null));

        FundWithdrawalResponse response = payoutService.initiate(
                neighborhoodId, fundId, withdrawalId, adminId);

        verify(payoutProvider).initiatePayout(request);
        verify(financeService).recordPayoutInitiation(attemptId, "fapshi-456");
        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("PAYOUT_PENDING");
    }
}
