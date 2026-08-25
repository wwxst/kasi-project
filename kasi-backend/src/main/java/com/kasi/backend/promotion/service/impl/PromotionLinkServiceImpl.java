package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.dto.PromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.service.PromotionLinkPersistenceService;
import com.kasi.backend.promotion.service.PromotionLinkPreparation;
import com.kasi.backend.promotion.service.PromotionLinkService;
import com.kasi.backend.promotion.vo.PromotionLinkPageVO;
import com.kasi.backend.promotion.vo.PromotionLinkVO;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionLinkServiceImpl implements PromotionLinkService {
    private final PromotionLinkPersistenceService persistenceService;
    private final PromotionLinkMapper linkMapper;

    @Override
    @Transactional(readOnly = true)
    public PromotionLinkPageVO getMine(Long userId, PromotionLinkPageQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();
        List<PromotionLinkVO> list = linkMapper.findPageByUserId(userId, offset, query.getSize())
                .stream().map(this::toVO).toList();
        return PromotionLinkPageVO.builder().list(list).page(query.getPage()).size(query.getSize())
                .total(linkMapper.countByUserId(userId)).build();
    }

    @Override
    public PromotionLinkVO createOrRetry(Long userId, CreatePromotionLinkDTO request) {
        PromotionLinkPreparation preparation = persistenceService.preparePending(userId, request);
        PromotionLink link = preparation.link();
        if (link.getStatus() == PromotionLinkStatus.SUCCESS) {
            return toVO(link);
        }

        try {
            PromotionLinkProviderAdapter adapter = (PromotionLinkProviderAdapter) preparation.runtime().adapter();
            var result = adapter.generatePromotionLink(preparation.runtime().secret(), preparation.providerRequest());
            return toVO(persistenceService.markSuccess(link.getId(), result.externalCode(), result.shareUrl(),
                    result.customParams(), userId, request.getRequestKey()));
        } catch (ProviderTransientException exception) {
            persistenceService.markFailed(link.getId(), "PROVIDER_REMOTE_UNAVAILABLE", exception.getMessage());
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
        } catch (ProviderRemoteRejectedException exception) {
            persistenceService.markFailed(link.getId(), "PROVIDER_REMOTE_REJECTED", exception.getMessage());
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }
    }

    private PromotionLinkVO toVO(PromotionLink link) {
        return PromotionLinkVO.builder().id(link.getId()).providerId(link.getProviderId()).providerName(link.getProviderName())
                .dramaId(link.getDramaId()).dramaTitle(link.getDramaTitle()).mediaAccountId(link.getMediaAccountId())
                .mediaType(link.getMediaType()).mediaAccountName(link.getMediaAccountName()).campaignName(link.getCampaignName())
                .trackingNo(link.getTrackingNo()).externalCode(link.getExternalCode()).shareUrl(link.getShareUrl())
                .customParams(link.getCustomParams()).landingType(link.getLandingType()).status(link.getStatus())
                .lastErrorCode(link.getLastErrorCode()).lastErrorMessage(link.getLastErrorMessage())
                .createdAt(link.getCreatedAt()).updatedAt(link.getUpdatedAt()).build();
    }
}
