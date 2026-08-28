package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.service.PromotionLinkPersistenceService;
import com.kasi.backend.promotion.service.PromotionLinkPreparation;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionLinkPersistenceServiceImpl implements PromotionLinkPersistenceService {
    private final PromotionLinkMapper linkMapper;
    private final PromotionUserMapper userMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final ProviderCommissionRuleService commissionRuleService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PromotionLinkPreparation> prepareBatchPending(Long userId, CreatePromotionLinkDTO request) {
        PromotionUser user = userMapper.findById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        ProviderDrama drama = dramaMapper.findById(request.getDramaId());
        if (drama == null || drama.getLocalStatus() != DramaLocalStatus.PUBLISHED
                || !"1".equals(drama.getRemoteShowStatus())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        ProviderRuntimeConnection runtime = runtimeService.resolve(request.getProviderId(), ProviderCapability.PROMOTION_LINK);
        if (!runtime.connectionId().equals(drama.getConnectionId())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        if (commissionRuleService.findDefaultRule(request.getProviderId()) == null) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_RULE_REQUIRED);
        }
        List<PromotionLinkPreparation> result = new ArrayList<>();
        String batchNo = linkMapper.findBatchByUserAndRequestKey(userId, request.getRequestKey()).stream()
                .findFirst().map(PromotionLink::getBatchNo)
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        for (String mediaTypeValue : request.getMediaTypes()) {
            MediaType mediaType = MediaType.valueOf(mediaTypeValue);
            for (String variant : List.of("LANDING", "ONELINK")) {
                PromotionLink link = linkMapper.findByUserAndRequestKeyForUpdate(userId, request.getRequestKey(), mediaType.name(), variant);
                if (link != null && link.getStatus() == PromotionLinkStatus.SUCCESS) {
                    result.add(new PromotionLinkPreparation(link, null, null));
                    continue;
                }
                if (link == null) {
                    link = new PromotionLink();
                    link.setUserId(userId); link.setProviderId(request.getProviderId());
                    link.setConnectionId(runtime.connectionId()); link.setDramaId(drama.getId());
                    link.setRequestKey(request.getRequestKey()); link.setTrackingNo(UUID.randomUUID().toString().replace("-", ""));
                    link.setBatchNo(batchNo); link.setMediaType(mediaType.name()); link.setLinkVariant(variant);
                     link.setCampaignName(trimToNull(request.getCampaignName()));
                    link.setStatus(PromotionLinkStatus.PENDING); linkMapper.insert(link);
                } else {
                    link.setTrackingNo(UUID.randomUUID().toString().replace("-", ""));
                    linkMapper.resetPending(link.getId(), PromotionLinkStatus.PENDING, link.getTrackingNo(), LocalDateTime.now());
                    link.setStatus(PromotionLinkStatus.PENDING);
                }
                result.add(new PromotionLinkPreparation(link, runtime,
                        new PromotionLinkRequest(drama.getExternalDramaId(), link.getTrackingNo(), mediaType, variant)));
            }
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PromotionLink markSuccess(Long linkId, String externalCode, String shareUrl, String customParams,
                                     Long userId, String requestKey, String mediaType, String linkVariant) {
        if (linkMapper.markSuccess(linkId, externalCode, shareUrl, customParams) != 1) {
            throw new IllegalStateException("推广链接成功状态更新未生效");
        }
        return linkMapper.findByUserAndRequestKey(userId, requestKey, mediaType, linkVariant);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long linkId, String errorCode, String errorMessage) {
        if (linkMapper.markFailed(linkId, errorCode, errorMessage) != 1) {
            throw new IllegalStateException("推广链接失败状态更新未生效");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
