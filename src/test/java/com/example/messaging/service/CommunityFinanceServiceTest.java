package com.example.messaging.service;

import com.example.messaging.entity.*;
import com.example.messaging.dto.FundWithdrawalResponse;
import com.example.messaging.payment.PaymentStatus;
import com.example.messaging.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityFinanceServiceTest {

    @Mock
    private CommunityFundRepository fundRepository;
    @Mock
    private CommunityContributionRepository contributionRepository;
    @Mock
    private FundLedgerEntryRepository ledgerRepository;
    @Mock
    private FundWithdrawalRepository withdrawalRepository;
    @Mock
    private FundWithdrawalApprovalRepository approvalRepository;
    @Mock
    private FundWithdrawalPayoutAttemptRepository payoutAttemptRepository;
    @Mock
    private NeighborhoodMembershipRepository membershipRepository;
    @Mock
    private NeighborhoodRepository neighborhoodRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CommunityFinanceService service;

    @Test
    void confirmedContributionCreditsNetFapshiRevenueOnlyOnce() {
        UUID contributionId = UUID.randomUUID();
        UUID fundId = UUID.randomUUID();
        UUID contributorId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        CommunityContribution contribution = CommunityContribution.builder()
                .id(contributionId)
                .fundId(fundId)
                .contributorId(contributorId)
                .amount(5_000)
                .externalId("contrib_123")
                .providerTransactionId(transactionId.toString())
                .status(ContributionStatus.PENDING)
                .build();
        when(contributionRepository.findByExternalIdForUpdate("contrib_123")).thenReturn(Optional.of(contribution));
        when(ledgerRepository.existsBySourceTypeAndSourceId(
                FundLedgerSourceType.CONTRIBUTION, contributionId)).thenReturn(false);
        when(fundRepository.findById(fundId)).thenReturn(Optional.of(CommunityFund.builder()
                .id(fundId)
                .neighborhoodId(UUID.randomUUID())
                .currency("XAF")
                .build()));
        when(ledgerRepository.save(any(FundLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyPaymentStatus(new PaymentStatus(
                transactionId.toString(), "contrib_123", "CONFIRMED", 5_000L, 4_850L));

        ArgumentCaptor<FundLedgerEntry> entryCaptor = ArgumentCaptor.forClass(FundLedgerEntry.class);
        verify(ledgerRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getType()).isEqualTo(FundLedgerEntryType.CREDIT);
        assertThat(entryCaptor.getValue().getAmount()).isEqualTo(4_850);
        assertThat(contribution.getStatus()).isEqualTo(ContributionStatus.CONFIRMED);
    }

    @Test
    void duplicateConfirmedPaymentDoesNotCreateAnotherLedgerEntry() {
        UUID contributionId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        CommunityContribution contribution = CommunityContribution.builder()
                .id(contributionId)
                .fundId(UUID.randomUUID())
                .contributorId(UUID.randomUUID())
                .amount(5_000)
                .externalId("contrib_123")
                .providerTransactionId(transactionId.toString())
                .status(ContributionStatus.CONFIRMED)
                .build();
        when(contributionRepository.findByExternalIdForUpdate("contrib_123")).thenReturn(Optional.of(contribution));

        service.applyPaymentStatus(new PaymentStatus(
                transactionId.toString(), "contrib_123", "CONFIRMED", 5_000L, 4_850L));

        verify(ledgerRepository, never()).save(any(FundLedgerEntry.class));
    }

    @Test
    void withdrawalIsApprovedOnlyAfterEverySnapshotAdminApproves() {
        UUID neighborhoodId = UUID.randomUUID();
        UUID fundId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID firstAdminId = UUID.randomUUID();
        UUID secondAdminId = UUID.randomUUID();
        CommunityFund fund = CommunityFund.builder()
                .id(fundId)
                .neighborhoodId(neighborhoodId)
                .currency("XAF")
                .build();
        FundWithdrawal withdrawal = FundWithdrawal.builder()
                .id(withdrawalId)
                .fundId(fundId)
                .requestedBy(UUID.randomUUID())
                .amount(1_000)
                .purpose("Road repair")
                .status(FundWithdrawalStatus.PENDING)
                .build();
        FundWithdrawalApproval firstApproval = approval(withdrawalId, firstAdminId,
                WithdrawalApprovalDecision.APPROVED);
        FundWithdrawalApproval secondApproval = approval(withdrawalId, secondAdminId,
                WithdrawalApprovalDecision.PENDING);

        when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(fundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(membershipRepository.findByNeighborhoodIdAndUserId(neighborhoodId, secondAdminId))
                .thenReturn(Optional.of(adminMembership(neighborhoodId, secondAdminId)));
        when(membershipRepository.findByNeighborhoodId(neighborhoodId))
                .thenReturn(List.of(
                        adminMembership(neighborhoodId, firstAdminId),
                        adminMembership(neighborhoodId, secondAdminId)));
        when(approvalRepository.findByWithdrawalId(withdrawalId))
                .thenReturn(List.of(firstApproval, secondApproval));
        when(approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, secondAdminId))
                .thenReturn(Optional.of(secondApproval));

        FundWithdrawalResponse response = service.decideWithdrawal(
                neighborhoodId, fundId, withdrawalId, secondAdminId, true);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.requiredApprovals()).isEqualTo(2);
        assertThat(response.approvalsReceived()).isEqualTo(2);
    }

    @Test
    void confirmedPayoutDebitsLedgerOnlyAfterProviderConfirmation() {
        UUID attemptId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        UUID fundId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String externalId = "payout_123";
        String providerTransactionId = "fapshi-456";
        FundWithdrawalPayoutAttempt attempt = FundWithdrawalPayoutAttempt.builder()
                .id(attemptId)
                .withdrawalId(withdrawalId)
                .externalId(externalId)
                .providerTransactionId(providerTransactionId)
                .amount(25_000)
                .initiatedBy(adminId)
                .status(FundWithdrawalPayoutStatus.PENDING)
                .build();
        FundWithdrawal withdrawal = FundWithdrawal.builder()
                .id(withdrawalId)
                .fundId(fundId)
                .amount(25_000)
                .status(FundWithdrawalStatus.PAYOUT_PENDING)
                .build();
        CommunityFund fund = CommunityFund.builder()
                .id(fundId)
                .neighborhoodId(UUID.randomUUID())
                .currency("XAF")
                .build();
        when(payoutAttemptRepository.findByExternalId(externalId)).thenReturn(Optional.of(attempt));
        when(payoutAttemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(ledgerRepository.existsBySourceTypeAndSourceId(
                FundLedgerSourceType.WITHDRAWAL, withdrawalId)).thenReturn(false);
        when(fundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(ledgerRepository.save(any(FundLedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyPayoutStatus(new PaymentStatus(
                providerTransactionId, externalId, "CONFIRMED", 25_000L, null));

        ArgumentCaptor<FundLedgerEntry> entryCaptor = ArgumentCaptor.forClass(FundLedgerEntry.class);
        verify(ledgerRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getType()).isEqualTo(FundLedgerEntryType.DEBIT);
        assertThat(entryCaptor.getValue().getAmount()).isEqualTo(25_000);
        assertThat(withdrawal.getStatus()).isEqualTo(FundWithdrawalStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(FundWithdrawalPayoutStatus.PAID);
    }

    private FundWithdrawalApproval approval(
            UUID withdrawalId,
            UUID adminId,
            WithdrawalApprovalDecision decision
    ) {
        return FundWithdrawalApproval.builder()
                .withdrawalId(withdrawalId)
                .adminId(adminId)
                .decision(decision)
                .build();
    }

    private NeighborhoodMembership adminMembership(UUID neighborhoodId, UUID adminId) {
        return NeighborhoodMembership.builder()
                .neighborhoodId(neighborhoodId)
                .userId(adminId)
                .status(NeighborhoodMembershipStatus.VERIFIED)
                .role(NeighborhoodMembershipRole.ADMIN)
                .build();
    }
}
