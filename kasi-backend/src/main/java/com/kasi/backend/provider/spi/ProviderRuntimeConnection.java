package com.kasi.backend.provider.spi;

public record ProviderRuntimeConnection(
        Long connectionId,
        Long providerId,
        String providerCode,
        String providerName,
        ProviderConnectionSecret secret,
        ProviderAdapter adapter) {
    @Override
    public String toString() {
        return "ProviderRuntimeConnection[connectionId=" + connectionId
                + ", providerId=" + providerId + ", providerCode=" + providerCode + "]";
    }
}
