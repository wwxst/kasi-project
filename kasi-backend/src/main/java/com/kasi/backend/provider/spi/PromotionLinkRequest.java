package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.MediaType;

public record PromotionLinkRequest(String externalDramaId,
                                   String userNo,
                                   MediaType mediaType,
                                   String linkVariant) {
}
