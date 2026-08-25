package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;

public record PromotionLinkPreparation(
        PromotionLink link,
        ProviderRuntimeConnection runtime,
        PromotionLinkRequest providerRequest) {
}
