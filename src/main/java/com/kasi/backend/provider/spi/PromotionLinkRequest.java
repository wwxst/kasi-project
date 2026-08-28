package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.MediaType;

public record PromotionLinkRequest(String externalDramaId,
                                   String trackingNo,
                                   MediaType mediaType,
                                   String linkVariant) {
}
