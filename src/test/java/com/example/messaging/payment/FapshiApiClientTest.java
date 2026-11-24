package com.example.messaging.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FapshiApiClientTest {

    @Test
    void verifiesWebhookSecretAndRejectsMissingConfiguration() {
        FapshiProperties configured = new FapshiProperties();
        configured.setWebhookSecret("local-test-secret");
        FapshiApiClient client = new FapshiApiClient(configured);

        assertThat(client.verifyWebhookSecret("local-test-secret")).isTrue();
        assertThat(client.verifyWebhookSecret("wrong-secret")).isFalse();
        assertThat(client.verifyWebhookSecret(null)).isFalse();

        FapshiApiClient unconfigured = new FapshiApiClient(new FapshiProperties());
        assertThat(unconfigured.verifyWebhookSecret("local-test-secret")).isFalse();
        assertThatThrownBy(unconfigured::getPayoutBalance)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payout credentials are not configured");
    }
}
