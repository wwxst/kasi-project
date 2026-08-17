package com.kasi.backend.provider.spi;

import lombok.Getter;

@Getter
public class ProviderConnectionSecret {

    private final String partnerId;
    private final String apiKey;
    private final String currency;

    public ProviderConnectionSecret(String partnerId, String apiKey, String currency) {
        this.partnerId = partnerId;
        this.apiKey = apiKey;
        this.currency = currency;
    }
}
