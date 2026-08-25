package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaAccountStatus;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
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

@Service
@RequiredArgsConstructor
public class PromotionLinkPersistenceServiceImpl implements PromotionLinkPersistenceService {
    private final PromotionLinkMapper linkMapper;
    private final PromotionUserMapper userMapper;
    private final PromotionMediaAccountMapper mediaMapper;
    private final ProviderMediaFilingMapper filingMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final ProviderCommissionRuleService commissionRuleService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PromotionLinkPreparation preparePending(Long userId, CreatePromotionLinkDTO request) {
        PromotionLink existing = linkMapper.findByUserAndRequestKeyForUpdate(userId, request.getRequestKey());
        if (existing != null && existing.getStatus() == PromotionLinkStatus.SUCCESS) {
            return new PromotionLinkPreparation(existing, null, null);
        }

        PromotionUser user = userMapper.findById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        PromotionMediaAccount media = mediaMapper.findOwnedById(request.getMediaAccountId(), userId);
        if (media == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        if (!Integer.valueOf(MediaAccountStatus.ENABLED.getCode()).equals(media.getStatus())) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DISABLED);
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
        ProviderMediaFiling filing = filingMapper.findByConnectionAndMedia(runtime.connectionId(), media.getId());
        if (filing == null || filing.getStatus() != FilingStatus.APPROVED) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_MEDIA_NOT_APPROVED);
        }

        PromotionLink link = existing;
        if (link == null) {
            link = new PromotionLink();
            link.setUserId(userId);
            link.setProviderId(request.getProviderId());
            link.setConnectionId(runtime.connectionId());
            link.setDramaId(drama.getId());
            link.setMediaAccountId(media.getId());
            link.setRequestKey(request.getRequestKey());
            link.setTrackingNo(UUID.randomUUID().toString().replace("-", ""));
            link.setCampaignName(trimToNull(request.getCampaignName()));
            link.setProviderCode(runtime.providerCode());
            link.setLandingType(request.getLandingType());
            link.setStatus(PromotionLinkStatus.PENDING);
            linkMapper.insert(link);
        } else {
            linkMapper.resetPending(link.getId(), PromotionLinkStatus.PENDING, LocalDateTime.now());
            link.setStatus(PromotionLinkStatus.PENDING);
        }

        return new PromotionLinkPreparation(link, runtime,
                new PromotionLinkRequest(drama.getExternalDramaId(), link.getTrackingNo(), media.getMediaType(),
                        request.getLandingType()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PromotionLink markSuccess(Long linkId, String externalCode, String shareUrl, String customParams,
                                     Long userId, String requestKey) {
        if (linkMapper.markSuccess(linkId, externalCode, shareUrl, customParams) != 1) {
            throw new IllegalStateException("推广链接成功状态更新未生效");
        }
        return linkMapper.findByUserAndRequestKey(userId, requestKey);
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
