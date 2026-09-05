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
import com.kasi.backend.promotion.vo.PromotionLinkBatchVO;
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
    public PromotionLinkBatchVO createOrRetry(Long userId, CreatePromotionLinkDTO request) {
        List<PromotionLinkVO> links = new java.util.ArrayList<>();
        for (PromotionLinkPreparation preparation : persistenceService.prepareBatchPending(userId, request)) {
            PromotionLink link = preparation.link();
            if (preparation.runtime() == null || link.getStatus() == PromotionLinkStatus.SUCCESS) {
                links.add(toVO(link));
                continue;
            }
            try {
                PromotionLinkProviderAdapter adapter = (PromotionLinkProviderAdapter) preparation.runtime().adapter();
                var result = adapter.generatePromotionLink(preparation.runtime().secret(), preparation.providerRequest());
                links.add(toVO(persistenceService.markSuccess(link.getId(), result.externalCode(), result.shareUrl(),
                        userId, request.getRequestKey(), link.getMediaType(), link.getLinkVariant())));
            } catch (ProviderTransientException exception) {
                persistenceService.markFailed(link.getId(), "PROVIDER_REMOTE_UNAVAILABLE", exception.getMessage());
                link.setStatus(PromotionLinkStatus.FAILED); link.setLastErrorCode("PROVIDER_REMOTE_UNAVAILABLE"); link.setLastErrorMessage(exception.getMessage());
                links.add(toVO(link));
            } catch (ProviderRemoteRejectedException exception) {
                persistenceService.markFailed(link.getId(), "PROVIDER_REMOTE_REJECTED", exception.getMessage());
                link.setStatus(PromotionLinkStatus.FAILED); link.setLastErrorCode("PROVIDER_REMOTE_REJECTED"); link.setLastErrorMessage(exception.getMessage());
                links.add(toVO(link));
            }
        }
        String batchNo = links.stream().map(PromotionLinkVO::getBatchNo).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return PromotionLinkBatchVO.builder().batchNo(batchNo).requestKey(request.getRequestKey()).links(links)
                .complete(links.stream().allMatch(link -> link.getStatus() == PromotionLinkStatus.SUCCESS)).build();
    }

    private PromotionLinkVO toVO(PromotionLink link) {
        return PromotionLinkVO.builder().id(link.getId()).providerId(link.getProviderId()).providerName(link.getProviderName())
                .dramaId(link.getDramaId()).dramaTitle(link.getDramaTitle()).batchNo(link.getBatchNo()).requestKey(link.getRequestKey())
                .mediaType(link.getMediaType()).linkVariant(link.getLinkVariant()).campaignName(link.getCampaignName())
                .trackingNo(link.getTrackingNo()).externalCode(link.getExternalCode()).shareUrl(link.getShareUrl())
                .status(link.getStatus())
                .lastErrorCode(link.getLastErrorCode()).lastErrorMessage(link.getLastErrorMessage())
                .createdAt(link.getCreatedAt()).updatedAt(link.getUpdatedAt()).build();
    }
}
