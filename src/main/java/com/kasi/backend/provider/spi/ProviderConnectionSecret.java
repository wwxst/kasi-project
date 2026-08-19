package com.kasi.backend.provider.spi;

import lombok.Getter;

@Getter
public class ProviderConnectionSecret {

    private final String baseUrl;
    private final String partnerId;
    private final String apiKey;
    private final String currency;

    public ProviderConnectionSecret(String baseUrl, String partnerId, String apiKey, String currency) {
        this.baseUrl = baseUrl;
        this.partnerId = partnerId;
        this.apiKey = apiKey;
        this.currency = currency;
    }
}
