package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.dto.PromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaAccountStatus;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.service.PromotionLinkService;
import com.kasi.backend.promotion.vo.PromotionLinkPageVO;
import com.kasi.backend.promotion.vo.PromotionLinkVO;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.PromotionLinkResult;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionLinkServiceImpl implements PromotionLinkService {
    private final PromotionLinkMapper linkMapper;
    private final PromotionUserMapper userMapper;
    private final PromotionMediaAccountMapper mediaMapper;
    private final ProviderMediaFilingMapper filingMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final ProviderCommissionRuleService commissionRuleService;

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
    @Transactional
    public PromotionLinkVO createOrRetry(Long userId, CreatePromotionLinkDTO request) {
        PromotionLink existing = linkMapper.findByUserAndRequestKeyForUpdate(userId, request.getRequestKey());
        if (existing != null && existing.getStatus() == PromotionLinkStatus.SUCCESS) return toVO(existing);

        PromotionUser user = userMapper.findById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(ErrorCode.USER_DISABLED);
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
            linkMapper.resetPending(link.getId(), PromotionLinkStatus.PENDING, java.time.LocalDateTime.now());
            link.setStatus(PromotionLinkStatus.PENDING);
        }

        try {
            PromotionLinkProviderAdapter adapter = (PromotionLinkProviderAdapter) runtime.adapter();
            PromotionLinkResult result = adapter.generatePromotionLink(runtime.secret(),
                    new PromotionLinkRequest(drama.getExternalDramaId(), link.getTrackingNo(), media.getMediaType(),
                            request.getLandingType()));
            linkMapper.markSuccess(link.getId(), result.externalCode(), result.shareUrl(), result.customParams());
        } catch (ProviderTransientException exception) {
            linkMapper.markFailed(link.getId(), "PROVIDER_REMOTE_UNAVAILABLE", exception.getMessage());
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
        } catch (ProviderRemoteRejectedException exception) {
            linkMapper.markFailed(link.getId(), "PROVIDER_REMOTE_REJECTED", exception.getMessage());
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }
        return toVO(linkMapper.findByUserAndRequestKey(userId, request.getRequestKey()));
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

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
