package com.example.messaging.controller;

import com.example.messaging.dto.*;
import com.example.messaging.service.CommunityContributionPaymentService;
import com.example.messaging.service.CommunityFinanceService;
import com.example.messaging.service.CommunityPayoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/neighborhoods/{neighborhoodId}/funds")
public class CommunityFinanceController {
    private final CommunityFinanceService financeService;
    private final CommunityContributionPaymentService contributionPaymentService;
    private final CommunityPayoutService payoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityFundResponse createFund(
            @PathVariable UUID neighborhoodId,
            @Valid @RequestBody CreateCommunityFundRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.createFund(neighborhoodId, request, currentUserId(jwt));
    }

    @GetMapping
    public List<CommunityFundResponse> listFunds(
            @PathVariable UUID neighborhoodId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.listFunds(neighborhoodId, currentUserId(jwt));
    }

    @GetMapping("/{fundId}")
    public CommunityFundResponse getFund(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.getFund(neighborhoodId, fundId, currentUserId(jwt));
    }

    @PostMapping("/{fundId}/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityContributionResponse contribute(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @Valid @RequestBody CreateContributionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return contributionPaymentService.initiate(
                neighborhoodId, fundId, request.amount(), currentUserId(jwt));
    }

    @PostMapping("/{fundId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public FundWithdrawalResponse requestWithdrawal(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @Valid @RequestBody CreateFundWithdrawalRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.requestWithdrawal(
                neighborhoodId, fundId, request, currentUserId(jwt));
    }

    @GetMapping("/{fundId}/withdrawals")
    public List<FundWithdrawalResponse> listWithdrawals(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.listWithdrawals(neighborhoodId, fundId, currentUserId(jwt));
    }

    @GetMapping("/{fundId}/withdrawals/{withdrawalId}")
    public FundWithdrawalResponse getWithdrawal(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.getWithdrawal(
                neighborhoodId, fundId, withdrawalId, currentUserId(jwt));
    }

    @GetMapping("/{fundId}/withdrawals/{withdrawalId}/payout-details")
    public FundWithdrawalPayoutDetailsResponse getPayoutDetails(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.getPayoutDetails(
                neighborhoodId, fundId, withdrawalId, currentUserId(jwt));
    }

    @PostMapping("/{fundId}/withdrawals/{withdrawalId}/approvals")
    public FundWithdrawalResponse decideWithdrawal(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody FundWithdrawalApprovalRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.decideWithdrawal(
                neighborhoodId, fundId, withdrawalId, currentUserId(jwt), request.approved());
    }

    @PostMapping("/{fundId}/withdrawals/{withdrawalId}/complete")
    public FundWithdrawalResponse completeWithdrawal(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @PathVariable UUID withdrawalId,
            @Valid @RequestBody CompleteFundWithdrawalRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return financeService.completeWithdrawal(
                neighborhoodId, fundId, withdrawalId, currentUserId(jwt), request.receiptReference());
    }

    @PostMapping("/{fundId}/withdrawals/{withdrawalId}/payouts")
    public FundWithdrawalResponse initiatePayout(
            @PathVariable UUID neighborhoodId,
            @PathVariable UUID fundId,
            @PathVariable UUID withdrawalId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return payoutService.initiate(
                neighborhoodId, fundId, withdrawalId, currentUserId(jwt));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
