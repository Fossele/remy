package com.example.messaging.controller;

import com.example.messaging.service.CommunityContributionPaymentService;
import com.example.messaging.service.CommunityPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks/fapshi")
public class FapshiWebhookController {
    private final CommunityContributionPaymentService paymentService;
    private final CommunityPayoutService payoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(
            @RequestHeader(name = "x-wh-secret", required = false) String secret,
            @RequestBody CommunityContributionPaymentService.FapshiWebhookPayload payload
    ) {
        if (payload != null && payload.externalId() != null
                && payload.externalId().startsWith("payout_")) {
            payoutService.processWebhook(secret, payload);
        } else {
            paymentService.processWebhook(secret, payload);
        }
    }
}
