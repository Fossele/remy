package com.example.messaging.service;

import com.example.messaging.dto.*;
import com.example.messaging.entity.*;
import com.example.messaging.payment.*;
import com.example.messaging.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityFinanceService {
    private final CommunityFundRepository fundRepository;
    private final CommunityContributionRepository contributionRepository;
    private final FundLedgerEntryRepository ledgerRepository;
    private final FundWithdrawalRepository withdrawalRepository;
    private final FundWithdrawalApprovalRepository approvalRepository;
    private final FundWithdrawalPayoutAttemptRepository payoutAttemptRepository;
    private final NeighborhoodMembershipRepository membershipRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final AuditService auditService;

    @Transactional
    public CommunityFundResponse createFund(
            UUID neighborhoodId,
            CreateCommunityFundRequest request,
            UUID creatorId
    ) {
        requireModerator(neighborhoodId, creatorId);
        neighborhoodRepository.findById(neighborhoodId)
                .filter(Neighborhood::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active neighborhood not found"));

        CommunityFund fund = fundRepository.save(CommunityFund.builder()
                .neighborhoodId(neighborhoodId)
                .name(request.name())
                .description(request.description())
                .targetAmount(request.targetAmount())
                .createdBy(creatorId)
                .build());
        auditService.record(creatorId, AuditAction.COMMUNITY_FUND_CREATED, AuditOutcome.SUCCESS,
                "COMMUNITY_FUND", fund.getId(), neighborhoodId, "Community project fund created");
        return toFundResponse(fund);
    }

    @Transactional(readOnly = true)
    public List<CommunityFundResponse> listFunds(UUID neighborhoodId, UUID userId) {
        requireVerifiedMember(neighborhoodId, userId);
        return fundRepository.findByNeighborhoodIdAndStatusOrderByCreatedAtDesc(
                        neighborhoodId, CommunityFundStatus.ACTIVE)
                .stream()
                .map(this::toFundResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityFundResponse getFund(UUID neighborhoodId, UUID fundId, UUID userId) {
        requireVerifiedMember(neighborhoodId, userId);
        return toFundResponse(findFund(neighborhoodId, fundId));
    }

    @Transactional
    public CommunityContribution createContribution(
            UUID neighborhoodId,
            UUID fundId,
            long amount,
            UUID contributorId
    ) {
        requireVerifiedMember(neighborhoodId, contributorId);
        CommunityFund fund = findFund(neighborhoodId, fundId);
        if (fund.getStatus() != CommunityFundStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fund is closed");
        }

        CommunityContribution contribution = contributionRepository.save(CommunityContribution.builder()
                .fundId(fundId)
                .contributorId(contributorId)
                .amount(amount)
                .externalId("contrib_" + UUID.randomUUID())
                .build());
        auditService.record(contributorId, AuditAction.COMMUNITY_CONTRIBUTION_CREATED, AuditOutcome.SUCCESS,
                "COMMUNITY_CONTRIBUTION", contribution.getId(), neighborhoodId,
                "Community contribution payment initiated");
        return contribution;
    }

    @Transactional
    public CommunityContribution recordPaymentInitiation(
            UUID contributionId,
            UUID contributorId,
            String providerTransactionId,
            String paymentLink
    ) {
        CommunityContribution contribution = findContribution(contributionId, contributorId);
        if (contribution.getStatus() != ContributionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contribution is no longer pending");
        }
        contribution.setProviderTransactionId(providerTransactionId);
        contribution.setPaymentLink(paymentLink);
        return contributionRepository.save(contribution);
    }

    @Transactional
    public void failPaymentInitiation(UUID contributionId, UUID contributorId) {
        CommunityContribution contribution = findContribution(contributionId, contributorId);
        if (contribution.getStatus() == ContributionStatus.PENDING) {
            contribution.setStatus(ContributionStatus.FAILED);
            contributionRepository.save(contribution);
        }
    }

    @Transactional
    public void applyPaymentStatus(PaymentStatus paymentStatus) {
        CommunityContribution contribution = contributionRepository.findByExternalIdForUpdate(paymentStatus.externalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contribution not found"));
        if (contribution.getProviderTransactionId() == null
                || !contribution.getProviderTransactionId().equals(paymentStatus.providerTransactionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction does not match contribution");
        }

        if (contribution.getStatus() != ContributionStatus.PENDING) {
            return;
        }

        switch (paymentStatus.status()) {
            case "PENDING" -> {
                return;
            }
            case "FAILED" -> contribution.setStatus(ContributionStatus.FAILED);
            case "EXPIRED" -> contribution.setStatus(ContributionStatus.EXPIRED);
            case "CONFIRMED" -> confirmContribution(contribution, paymentStatus);
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unsupported payment status");
        }
        contributionRepository.save(contribution);
    }

    @Transactional
    public FundWithdrawalResponse requestWithdrawal(
            UUID neighborhoodId,
            UUID fundId,
            CreateFundWithdrawalRequest request,
            UUID requesterId
    ) {
        requireVerifiedMember(neighborhoodId, requesterId);
        CommunityFund fund = fundRepository.findByIdForUpdate(fundId)
                .filter(candidate -> candidate.getNeighborhoodId().equals(neighborhoodId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
        if (fund.getStatus() != CommunityFundStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fund is closed");
        }

        List<UUID> adminIds = verifiedAdminIds(neighborhoodId);
        if (adminIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The neighborhood has no verified administrators");
        }

        long reserved = reservedAmount(fundId);
        long available = ledgerBalance(fundId) - reserved;
        if (request.amount() > available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal exceeds available fund balance");
        }

        FundWithdrawal withdrawal = withdrawalRepository.save(FundWithdrawal.builder()
                .fundId(fundId)
                .requestedBy(requesterId)
                .amount(request.amount())
                .purpose(request.purpose())
                .beneficiaryName(request.beneficiaryName())
                .beneficiaryPhone(request.beneficiaryPhone())
                .beneficiaryMedium(request.beneficiaryMedium())
                .build());
        approvalRepository.saveAll(adminIds.stream()
                .map(adminId -> FundWithdrawalApproval.builder()
                        .withdrawalId(withdrawal.getId())
                        .adminId(adminId)
                        .build())
                .toList());
        auditService.record(requesterId, AuditAction.FUND_WITHDRAWAL_REQUESTED, AuditOutcome.SUCCESS,
                "FUND_WITHDRAWAL", withdrawal.getId(), neighborhoodId, "Community fund withdrawal requested");
        return toWithdrawalResponse(withdrawal, fund.getCurrency(), requesterId);
    }

    @Transactional(readOnly = true)
    public List<FundWithdrawalResponse> listWithdrawals(UUID neighborhoodId, UUID fundId, UUID userId) {
        requireVerifiedMember(neighborhoodId, userId);
        CommunityFund fund = findFund(neighborhoodId, fundId);
        return withdrawalRepository.findByFundIdOrderByCreatedAtDesc(fundId).stream()
                .map(withdrawal -> toWithdrawalResponse(withdrawal, fund.getCurrency(), userId))
                .toList();
    }

    @Transactional
    public FundWithdrawalResponse decideWithdrawal(
            UUID neighborhoodId,
            UUID fundId,
            UUID withdrawalId,
            UUID adminId,
            boolean approved
    ) {
        FundWithdrawal withdrawal = findWithdrawalForUpdate(neighborhoodId, fundId, withdrawalId);
        CommunityFund fund = findFund(neighborhoodId, fundId);
        if (withdrawal.getStatus() != FundWithdrawalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal is no longer pending approval");
        }
        requireModerator(neighborhoodId, adminId);
        if (!adminRosterUnchanged(neighborhoodId, withdrawalId)) {
            withdrawal.setStatus(FundWithdrawalStatus.CANCELLED);
            withdrawalRepository.save(withdrawal);
            return toWithdrawalResponse(withdrawal, fund.getCurrency(), adminId);
        }

        FundWithdrawalApproval approval = approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only administrators listed when the withdrawal was requested may approve it"));
        if (approval.getDecision() != WithdrawalApprovalDecision.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrator has already decided this request");
        }
        approval.setDecision(approved
                ? WithdrawalApprovalDecision.APPROVED
                : WithdrawalApprovalDecision.REJECTED);
        approval.setDecidedAt(Instant.now());
        approvalRepository.save(approval);

        List<FundWithdrawalApproval> approvals = approvalRepository.findByWithdrawalId(withdrawalId);
        if (!approved) {
            withdrawal.setStatus(FundWithdrawalStatus.REJECTED);
        } else if (approvals.stream().allMatch(item -> item.getDecision() == WithdrawalApprovalDecision.APPROVED)) {
            withdrawal.setStatus(FundWithdrawalStatus.APPROVED);
        }
        withdrawalRepository.save(withdrawal);
        auditService.record(adminId, AuditAction.FUND_WITHDRAWAL_APPROVAL_RECORDED,
                approved ? AuditOutcome.SUCCESS : AuditOutcome.DENIED,
                "FUND_WITHDRAWAL", withdrawalId, neighborhoodId,
                approved ? "Withdrawal approved by administrator" : "Withdrawal rejected by administrator");
        return toWithdrawalResponse(withdrawal, fund.getCurrency(), adminId);
    }

    @Transactional
    public FundWithdrawalResponse completeWithdrawal(
            UUID neighborhoodId,
            UUID fundId,
            UUID withdrawalId,
            UUID adminId,
            String receiptReference
    ) {
        FundWithdrawal withdrawal = findWithdrawalForUpdate(neighborhoodId, fundId, withdrawalId);
        CommunityFund fund = findFund(neighborhoodId, fundId);
        requireModerator(neighborhoodId, adminId);
        if (!adminRosterUnchanged(neighborhoodId, withdrawalId)) {
            withdrawal.setStatus(FundWithdrawalStatus.CANCELLED);
            withdrawalRepository.save(withdrawal);
            return toWithdrawalResponse(withdrawal, fund.getCurrency(), adminId);
        }
        if (withdrawal.getStatus() != FundWithdrawalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All administrators must approve before completion");
        }
        if (approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, adminId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a listed administrator may complete this withdrawal");
        }
        if (ledgerRepository.existsBySourceTypeAndSourceId(FundLedgerSourceType.WITHDRAWAL, withdrawalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Withdrawal was already recorded in the ledger");
        }

        ledgerRepository.save(FundLedgerEntry.builder()
                .fundId(fundId)
                .type(FundLedgerEntryType.DEBIT)
                .amount(withdrawal.getAmount())
                .currency(fund.getCurrency())
                .sourceType(FundLedgerSourceType.WITHDRAWAL)
                .sourceId(withdrawalId)
                .createdBy(adminId)
                .build());
        withdrawal.setStatus(FundWithdrawalStatus.MANUALLY_PAID);
        withdrawal.setReceiptReference(receiptReference);
        withdrawal.setPaidAt(Instant.now());
        withdrawalRepository.save(withdrawal);
        auditService.record(adminId, AuditAction.FUND_WITHDRAWAL_COMPLETED, AuditOutcome.SUCCESS,
                "FUND_WITHDRAWAL", withdrawalId, neighborhoodId, "Approved withdrawal marked manually paid");
        return toWithdrawalResponse(withdrawal, fund.getCurrency(), adminId);
    }

    @Transactional(readOnly = true)
    public FundWithdrawalResponse getWithdrawal(
            UUID neighborhoodId, UUID fundId, UUID withdrawalId, UUID userId) {
        requireVerifiedMember(neighborhoodId, userId);
        FundWithdrawal withdrawal = findWithdrawal(neighborhoodId, fundId, withdrawalId);
        return toWithdrawalResponse(withdrawal, findFund(neighborhoodId, fundId).getCurrency(), userId);
    }

    @Transactional(readOnly = true)
    public FundWithdrawalPayoutDetailsResponse getPayoutDetails(
            UUID neighborhoodId, UUID fundId, UUID withdrawalId, UUID adminId) {
        FundWithdrawal withdrawal = findWithdrawal(neighborhoodId, fundId, withdrawalId);
        if (approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, adminId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only listed administrators may view payout details");
        }
        requireAdministrator(neighborhoodId, adminId);
        return FundWithdrawalPayoutDetailsResponse.fromEntity(withdrawal);
    }

    @Transactional
    public long validatePayoutAllowed(
            UUID neighborhoodId, UUID fundId, UUID withdrawalId, UUID adminId) {
        FundWithdrawal withdrawal = findWithdrawal(neighborhoodId, fundId, withdrawalId);
        requireModerator(neighborhoodId, adminId);
        if (withdrawal.getStatus() != FundWithdrawalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All administrators must approve before payout");
        }
        if (!adminRosterUnchanged(neighborhoodId, withdrawalId)) {
            withdrawal.setStatus(FundWithdrawalStatus.CANCELLED);
            withdrawalRepository.save(withdrawal);
            return 0;
        }
        if (approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, adminId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a listed administrator may initiate payout");
        }
        if (withdrawal.getAmount() > 500_000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Fapshi payouts are limited to 500000 XAF per request");
        }
        return withdrawal.getAmount();
    }

    @Transactional
    public PayoutOrder beginPayout(
            UUID neighborhoodId, UUID fundId, UUID withdrawalId, UUID adminId) {
        FundWithdrawal withdrawal = findWithdrawalForUpdate(neighborhoodId, fundId, withdrawalId);
        CommunityFund fund = findFund(neighborhoodId, fundId);
        requireModerator(neighborhoodId, adminId);
        if (withdrawal.getStatus() != FundWithdrawalStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All administrators must approve before payout");
        }
        if (!adminRosterUnchanged(neighborhoodId, withdrawalId)) {
            withdrawal.setStatus(FundWithdrawalStatus.CANCELLED);
            withdrawalRepository.save(withdrawal);
            return null;
        }
        if (approvalRepository.findByWithdrawalIdAndAdminId(withdrawalId, adminId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a listed administrator may initiate payout");
        }
        if (withdrawal.getAmount() > 500_000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Fapshi payouts are limited to 500000 XAF per request");
        }

        FundWithdrawalPayoutAttempt attempt = payoutAttemptRepository.save(FundWithdrawalPayoutAttempt.builder()
                .withdrawalId(withdrawalId)
                .externalId("payout_" + UUID.randomUUID())
                .amount(withdrawal.getAmount())
                .initiatedBy(adminId)
                .build());
        withdrawal.setStatus(FundWithdrawalStatus.PAYOUT_INITIATING);
        withdrawalRepository.save(withdrawal);
        auditService.record(adminId, AuditAction.FUND_WITHDRAWAL_PAYOUT_INITIATED, AuditOutcome.SUCCESS,
                "FUND_WITHDRAWAL", withdrawalId, neighborhoodId, "Fapshi payout initiation requested");
        return new PayoutOrder(
                attempt.getId(),
                new PayoutRequest(
                        withdrawal.getAmount(),
                        withdrawal.getBeneficiaryPhone(),
                        withdrawal.getBeneficiaryMedium(),
                        withdrawal.getBeneficiaryName(),
                        adminId.toString(),
                        attempt.getExternalId(),
                        withdrawal.getPurpose()
                )
        );
    }

    @Transactional
    public void recordPayoutInitiation(UUID attemptId, String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new IllegalArgumentException("Fapshi payout transaction ID is required");
        }
        FundWithdrawalPayoutAttempt attempt = findPayoutAttemptForUpdate(attemptId);
        if (attempt.getStatus() != FundWithdrawalPayoutStatus.INITIATING) {
            return;
        }
        attempt.setProviderTransactionId(providerTransactionId);
        attempt.setStatus(FundWithdrawalPayoutStatus.PENDING);
        payoutAttemptRepository.save(attempt);

        FundWithdrawal withdrawal = findWithdrawalByIdForUpdate(attempt.getWithdrawalId());
        if (withdrawal.getStatus() == FundWithdrawalStatus.PAYOUT_INITIATING) {
            withdrawal.setStatus(FundWithdrawalStatus.PAYOUT_PENDING);
            withdrawalRepository.save(withdrawal);
        }
    }

    @Transactional
    public void failPayoutInitiation(UUID attemptId) {
        FundWithdrawalPayoutAttempt attempt = findPayoutAttemptForUpdate(attemptId);
        if (attempt.getStatus() != FundWithdrawalPayoutStatus.INITIATING) {
            return;
        }
        attempt.setStatus(FundWithdrawalPayoutStatus.FAILED);
        payoutAttemptRepository.save(attempt);
        FundWithdrawal withdrawal = findWithdrawalByIdForUpdate(attempt.getWithdrawalId());
        if (withdrawal.getStatus() == FundWithdrawalStatus.PAYOUT_INITIATING) {
            withdrawal.setStatus(FundWithdrawalStatus.APPROVED);
            withdrawalRepository.save(withdrawal);
        }
        CommunityFund fund = fundRepository.findById(withdrawal.getFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
        auditService.record(attempt.getInitiatedBy(), AuditAction.FUND_WITHDRAWAL_PAYOUT_FAILED,
                AuditOutcome.FAILURE, "FUND_WITHDRAWAL", withdrawal.getId(),
                fund.getNeighborhoodId(), "Fapshi payout initiation failed");
    }

    @Transactional
    public void applyPayoutStatus(PaymentStatus paymentStatus) {
        if (paymentStatus.externalId() == null
                || paymentStatus.providerTransactionId() == null
                || paymentStatus.amount() == null
                || paymentStatus.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fapshi returned an incomplete payout status");
        }
        FundWithdrawalPayoutAttempt match = payoutAttemptRepository.findByExternalId(paymentStatus.externalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout attempt not found"));
        FundWithdrawalPayoutAttempt attempt = findPayoutAttemptForUpdate(match.getId());
        if (attempt.getProviderTransactionId() == null
                || !attempt.getProviderTransactionId().equals(paymentStatus.providerTransactionId())
                || attempt.getAmount() != paymentStatus.amount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payout transaction does not match request");
        }
        if (attempt.getStatus() == FundWithdrawalPayoutStatus.PAID
                || attempt.getStatus() == FundWithdrawalPayoutStatus.FAILED) {
            return;
        }

        FundWithdrawal withdrawal = findWithdrawalByIdForUpdate(attempt.getWithdrawalId());
        switch (paymentStatus.status()) {
            case "PENDING" -> {
                return;
            }
            case "FAILED", "EXPIRED" -> {
                attempt.setStatus(FundWithdrawalPayoutStatus.FAILED);
                withdrawal.setStatus(FundWithdrawalStatus.APPROVED);
                CommunityFund fund = fundRepository.findById(withdrawal.getFundId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
                auditService.record(attempt.getInitiatedBy(), AuditAction.FUND_WITHDRAWAL_PAYOUT_FAILED,
                        AuditOutcome.FAILURE, "FUND_WITHDRAWAL", withdrawal.getId(),
                        fund.getNeighborhoodId(), "Fapshi payout failed");
            }
            case "CONFIRMED" -> {
                CommunityFund fund = fundRepository.findById(withdrawal.getFundId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
                if (!ledgerRepository.existsBySourceTypeAndSourceId(
                        FundLedgerSourceType.WITHDRAWAL, withdrawal.getId())) {
                    ledgerRepository.save(FundLedgerEntry.builder()
                            .fundId(withdrawal.getFundId())
                            .type(FundLedgerEntryType.DEBIT)
                            .amount(withdrawal.getAmount())
                            .currency(fund.getCurrency())
                            .sourceType(FundLedgerSourceType.WITHDRAWAL)
                            .sourceId(withdrawal.getId())
                            .createdBy(attempt.getInitiatedBy())
                            .build());
                }
                attempt.setStatus(FundWithdrawalPayoutStatus.PAID);
                withdrawal.setStatus(FundWithdrawalStatus.PAID);
                withdrawal.setReceiptReference(paymentStatus.providerTransactionId());
                withdrawal.setPaidAt(Instant.now());
                auditService.record(attempt.getInitiatedBy(), AuditAction.FUND_WITHDRAWAL_COMPLETED,
                        AuditOutcome.SUCCESS, "FUND_WITHDRAWAL", withdrawal.getId(),
                        fund.getNeighborhoodId(),
                        "Fapshi payout confirmed");
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unsupported payout status");
        }
        payoutAttemptRepository.save(attempt);
        withdrawalRepository.save(withdrawal);
    }

    @Transactional(readOnly = true)
    public List<FundWithdrawalPayoutAttempt> findPendingPayoutAttempts(Instant createdBefore) {
        return payoutAttemptRepository.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(FundWithdrawalPayoutStatus.INITIATING, FundWithdrawalPayoutStatus.PENDING), createdBefore);
    }

    @Transactional(readOnly = true)
    public List<CommunityContribution> findPendingContributions(Instant createdBefore) {
        return contributionRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                ContributionStatus.PENDING, createdBefore);
    }

    private void confirmContribution(CommunityContribution contribution, PaymentStatus paymentStatus) {
        if (paymentStatus.amount() == null || paymentStatus.amount() != contribution.getAmount()
                || paymentStatus.netAmount() == null || paymentStatus.netAmount() <= 0
                || paymentStatus.netAmount() > contribution.getAmount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment amount does not match contribution");
        }
        if (ledgerRepository.existsBySourceTypeAndSourceId(
                FundLedgerSourceType.CONTRIBUTION, contribution.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contribution is already in the fund ledger");
        }
        CommunityFund fund = fundRepository.findById(contribution.getFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
        contribution.setNetAmount(paymentStatus.netAmount());
        contribution.setStatus(ContributionStatus.CONFIRMED);
        contribution.setConfirmedAt(Instant.now());
        contributionRepository.save(contribution);
        ledgerRepository.save(FundLedgerEntry.builder()
                .fundId(contribution.getFundId())
                .type(FundLedgerEntryType.CREDIT)
                .amount(paymentStatus.netAmount())
                .currency(fund.getCurrency())
                .sourceType(FundLedgerSourceType.CONTRIBUTION)
                .sourceId(contribution.getId())
                .createdBy(contribution.getContributorId())
                .build());
        auditService.record(contribution.getContributorId(), AuditAction.COMMUNITY_CONTRIBUTION_CONFIRMED,
                AuditOutcome.SUCCESS, "COMMUNITY_CONTRIBUTION", contribution.getId(),
                fund.getNeighborhoodId(), "Fapshi payment confirmed");
    }

    private CommunityFundResponse toFundResponse(CommunityFund fund) {
        long credited = contributionRepository.findByFundId(fund.getId()).stream()
                .filter(contribution -> contribution.getStatus() == ContributionStatus.CONFIRMED)
                .mapToLong(CommunityContribution::getAmount)
                .sum();
        long balance = ledgerBalance(fund.getId());
        long reserved = reservedAmount(fund.getId());
        if (balance < reserved) {
            throw new IllegalStateException("Reserved withdrawals exceed the fund balance");
        }
        return CommunityFundResponse.fromEntity(fund, credited, balance - reserved, reserved);
    }

    private FundWithdrawalResponse toWithdrawalResponse(
            FundWithdrawal withdrawal, String currency, UUID viewerId) {
        List<FundWithdrawalApproval> approvals = approvalRepository.findByWithdrawalId(withdrawal.getId());
        FundWithdrawalPayoutAttempt payoutAttempt = payoutAttemptRepository
                .findFirstByWithdrawalIdOrderByCreatedAtDesc(withdrawal.getId())
                .orElse(null);
        return FundWithdrawalResponse.fromEntity(
                withdrawal,
                currency,
                approvals.size(),
                (int) approvals.stream()
                        .filter(approval -> approval.getDecision() == WithdrawalApprovalDecision.APPROVED)
                        .count(),
                canViewPayoutPhone(withdrawal, approvals, viewerId)
                        ? withdrawal.getBeneficiaryPhone() : maskPhone(withdrawal.getBeneficiaryPhone()),
                payoutAttempt == null ? null : payoutAttempt.getProviderTransactionId(),
                payoutAttempt == null ? null : payoutAttempt.getStatus().name()
        );
    }

    private long ledgerBalance(UUID fundId) {
        return ledgerRepository.findByFundId(fundId).stream()
                .mapToLong(entry -> entry.getType() == FundLedgerEntryType.CREDIT
                        ? entry.getAmount() : -entry.getAmount())
                .sum();
    }

    private long reservedAmount(UUID fundId) {
        return withdrawalRepository.findByFundIdOrderByCreatedAtDesc(fundId).stream()
                .filter(withdrawal -> withdrawal.getStatus() == FundWithdrawalStatus.PENDING
                        || withdrawal.getStatus() == FundWithdrawalStatus.APPROVED
                        || withdrawal.getStatus() == FundWithdrawalStatus.PAYOUT_INITIATING
                        || withdrawal.getStatus() == FundWithdrawalStatus.PAYOUT_PENDING)
                .mapToLong(FundWithdrawal::getAmount)
                .sum();
    }

    private boolean adminRosterUnchanged(UUID neighborhoodId, UUID withdrawalId) {
        List<UUID> currentAdminIds = verifiedAdminIds(neighborhoodId);
        List<UUID> snapshotAdminIds = approvalRepository.findByWithdrawalId(withdrawalId).stream()
                .map(FundWithdrawalApproval::getAdminId)
                .sorted()
                .toList();
        return currentAdminIds.equals(snapshotAdminIds);
    }

    private List<UUID> verifiedAdminIds(UUID neighborhoodId) {
        return membershipRepository.findByNeighborhoodId(neighborhoodId).stream()
                .filter(membership -> membership.getStatus() == NeighborhoodMembershipStatus.VERIFIED
                        && membership.getRole() == NeighborhoodMembershipRole.ADMIN)
                .map(NeighborhoodMembership::getUserId)
                .sorted()
                .toList();
    }

    private FundWithdrawal findWithdrawalForUpdate(UUID neighborhoodId, UUID fundId, UUID withdrawalId) {
        return withdrawalRepository.findByIdForUpdate(withdrawalId)
                .filter(withdrawal -> withdrawal.getFundId().equals(fundId))
                .filter(withdrawal -> fundRepository.findById(fundId)
                        .filter(fund -> fund.getNeighborhoodId().equals(neighborhoodId))
                        .isPresent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found"));
    }

    private FundWithdrawal findWithdrawal(UUID neighborhoodId, UUID fundId, UUID withdrawalId) {
        return withdrawalRepository.findById(withdrawalId)
                .filter(withdrawal -> withdrawal.getFundId().equals(fundId))
                .filter(withdrawal -> fundRepository.findById(fundId)
                        .filter(fund -> fund.getNeighborhoodId().equals(neighborhoodId))
                        .isPresent())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found"));
    }

    private FundWithdrawal findWithdrawalByIdForUpdate(UUID withdrawalId) {
        return withdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Withdrawal not found"));
    }

    private FundWithdrawalPayoutAttempt findPayoutAttemptForUpdate(UUID attemptId) {
        return payoutAttemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout attempt not found"));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 3) {
            return "***";
        }
        return "*".repeat(phone.length() - 2) + phone.substring(phone.length() - 2);
    }

    private boolean canViewPayoutPhone(
            FundWithdrawal withdrawal, List<FundWithdrawalApproval> approvals, UUID viewerId) {
        if (approvals.stream().noneMatch(approval -> approval.getAdminId().equals(viewerId))) {
            return false;
        }
        return fundRepository.findById(withdrawal.getFundId())
                .flatMap(fund -> membershipRepository
                        .findByNeighborhoodIdAndUserId(fund.getNeighborhoodId(), viewerId))
                .filter(membership -> membership.getStatus() == NeighborhoodMembershipStatus.VERIFIED
                        && membership.getRole() == NeighborhoodMembershipRole.ADMIN)
                .isPresent();
    }

    private CommunityFund findFund(UUID neighborhoodId, UUID fundId) {
        return fundRepository.findById(fundId)
                .filter(fund -> fund.getNeighborhoodId().equals(neighborhoodId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fund not found"));
    }

    private CommunityContribution findContribution(UUID contributionId, UUID contributorId) {
        return contributionRepository.findById(contributionId)
                .filter(contribution -> contribution.getContributorId().equals(contributorId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contribution not found"));
    }

    private void requireVerifiedMember(UUID neighborhoodId, UUID userId) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verified neighborhood membership required");
        }
    }

    private void requireModerator(UUID neighborhoodId, UUID userId) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED
                || (membership.getRole() != NeighborhoodMembershipRole.ADMIN
                && membership.getRole() != NeighborhoodMembershipRole.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Moderator privileges required");
        }
    }

    private void requireAdministrator(UUID neighborhoodId, UUID userId) {
        NeighborhoodMembership membership = membershipRepository
                .findByNeighborhoodIdAndUserId(neighborhoodId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Neighborhood membership required"));
        if (membership.getStatus() != NeighborhoodMembershipStatus.VERIFIED
                || membership.getRole() != NeighborhoodMembershipRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator privileges required");
        }
    }
}
