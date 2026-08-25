package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
public interface PromotionLinkPersistenceService {
    PromotionLinkPreparation preparePending(Long userId, CreatePromotionLinkDTO request);

    PromotionLink markSuccess(Long linkId, String externalCode, String shareUrl, String customParams,
                              Long userId, String requestKey);

    void markFailed(Long linkId, String errorCode, String errorMessage);
}
