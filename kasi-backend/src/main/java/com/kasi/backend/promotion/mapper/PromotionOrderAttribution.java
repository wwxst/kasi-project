package com.kasi.backend.promotion.mapper;

/**
 * The provider order contract identifies a user and drama, but not a link variant.
 */
public record PromotionOrderAttribution(Long userId, Long dramaId) {
}
