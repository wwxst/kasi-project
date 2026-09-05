package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import java.util.List;
public interface PromotionLinkPersistenceService {
    List<PromotionLinkPreparation> prepareBatchPending(Long userId, CreatePromotionLinkDTO request);

    PromotionLink markSuccess(Long linkId, String externalCode, String shareUrl,
                              Long userId, String requestKey, String mediaType, String linkVariant);

    void markFailed(Long linkId, String errorCode, String errorMessage);
}
