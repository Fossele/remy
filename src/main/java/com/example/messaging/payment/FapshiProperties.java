package com.example.messaging.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.fapshi")
public class FapshiProperties {
    private String baseUrl = "https://sandbox.fapshi.com";
    private String collectionApiUser = "";
    private String collectionApiKey = "";
    private String payoutApiUser = "";
    private String payoutApiKey = "";
    private String webhookSecret = "";
    private String webhookUrl = "";
    private String redirectUrl = "";
}
