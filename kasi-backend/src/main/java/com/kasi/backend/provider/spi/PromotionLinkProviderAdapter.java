package com.kasi.backend.provider.spi;

public interface PromotionLinkProviderAdapter extends ProviderAdapter {
    PromotionLinkResult generatePromotionLink(ProviderConnectionSecret connection,
                                               PromotionLinkRequest request);
}
