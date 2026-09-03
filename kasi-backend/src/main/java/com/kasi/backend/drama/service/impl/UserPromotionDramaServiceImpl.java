package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.service.DramaMediaUrlValidator;
import com.kasi.backend.drama.vo.DramaListItemVO;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.drama.vo.DramaContentVO;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaContentResourceVO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class UserPromotionDramaServiceImpl implements UserPromotionDramaService {
    private final ProviderDramaMapper dramaMapper;
    private final DramaMediaUrlValidator mediaUrlValidator;
    private final ShortDramaConnectionMapper connectionMapper;
    private final tools.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    @Transactional(readOnly = true)
    public DramaPageVO getPublished(DramaPageQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();
        return DramaPageVO.builder().list(dramaMapper.pagePublished(offset, query.getSize()).stream()
                        .map(this::toVO).toList()).page(query.getPage()).size(query.getSize())
                .total(dramaMapper.countPublished()).build();
    }

    private DramaListItemVO toVO(ProviderDrama drama) {
        return DramaListItemVO.builder().id(drama.getId()).providerId(drama.getProviderId()).providerName(drama.getProviderName())
                .externalDramaId(drama.getExternalDramaId())
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).titleZh(drama.getTitleZh())
                .description(drama.getDescription()).coverUrl(drama.getCoverUrl()).labelNames(parseLabels(drama.getLabelNames()))
                .categoryName(drama.getCategoryName()).language(drama.getLanguage()).remoteRank(drama.getRemoteRank())
                .dramaType(drama.getDramaType()).novelType(drama.getNovelType()).novelSubType(drama.getNovelSubType())
                .commissionScopes(parseScopes(drama.getCommissionScope()))
                .promotionDescription(drama.getPromotionDescription())
                .remoteShowStatus(drama.getRemoteShowStatus())
                .localStatus(drama.getLocalStatus()).remoteCreatedAt(drama.getRemoteCreatedAt()).remoteUpdatedAt(drama.getRemoteUpdatedAt())
                .updatedAt(drama.getUpdatedAt()).build();
    }

    private List<PromotionCommissionScope> parseScopes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .map(PromotionCommissionScope::valueOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DramaDetailVO getPublishedDetail(Long id) {
        ProviderDrama drama = dramaMapper.findById(id);
        if (drama == null || drama.getLocalStatus() != DramaLocalStatus.PUBLISHED
                || !"1".equals(drama.getRemoteShowStatus())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        List<DramaContentVO> contents = dramaMapper.findContents(id).stream()
                .map(content -> DramaContentVO.builder().id(content.getId())
                        .externalContentId(content.getExternalContentId()).sequenceNo(content.getSequenceNo())
                        .title(content.getTitle()).free(Boolean.TRUE.equals(content.getFree()))
                        .durationSeconds(content.getDurationSeconds()).remoteUpdatedAt(content.getRemoteUpdatedAt()).build())
                .toList();
        return DramaDetailVO.builder().id(drama.getId()).externalDramaId(drama.getExternalDramaId())
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).titleZh(drama.getTitleZh())
                .description(drama.getDescription()).coverUrl(drama.getCoverUrl()).labelNames(parseLabels(drama.getLabelNames()))
                .categoryName(drama.getCategoryName()).language(drama.getLanguage()).remoteRank(drama.getRemoteRank())
                .dramaType(drama.getDramaType()).novelType(drama.getNovelType()).novelSubType(drama.getNovelSubType())
                .commissionScopes(parseScopes(drama.getCommissionScope())).promotionDescription(drama.getPromotionDescription())
                .remoteShowStatus(drama.getRemoteShowStatus()).localStatus(drama.getLocalStatus())
                .remoteCreatedAt(drama.getRemoteCreatedAt()).remoteUpdatedAt(drama.getRemoteUpdatedAt())
                .lastSeenAt(drama.getLastSeenAt()).createdAt(drama.getCreatedAt()).updatedAt(drama.getUpdatedAt())
                .contents(contents).build();
    }

    @Override
    public List<DramaContentResourceVO> getFreeContent(Long id) {
        return getFreeContent(id, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DramaContentResourceVO> getFreeContent(Long id, boolean refresh) {
        ProviderDrama drama = requirePublishedDrama(id);
        ShortDramaConnection connection = connectionMapper.findById(drama.getConnectionId());
        String mediaRootDomain = connection == null ? null : connection.getMediaRootDomain();
        return dramaMapper.findContents(id).stream().map(content -> {
            String url = Boolean.TRUE.equals(content.getFree())
                    && mediaUrlValidator.isAllowed(content.getContentUrl(), mediaRootDomain)
                    ? content.getContentUrl() : null;
            return DramaContentResourceVO.builder().id(content.getId()).sequenceNo(content.getSequenceNo())
                    .title(content.getTitle()).free(Boolean.TRUE.equals(content.getFree()))
                    .playUrl(url).downloadUrl(url).build();
        }).toList();
    }

    private ProviderDrama requirePublishedDrama(Long id) {
        ProviderDrama drama = dramaMapper.findById(id);
        if (drama == null || drama.getLocalStatus() != DramaLocalStatus.PUBLISHED
                || !"1".equals(drama.getRemoteShowStatus())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        return drama;
    }

    private List<String> parseLabels(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return List.of(objectMapper.readValue(value, String[].class));
        } catch (tools.jackson.core.JacksonException exception) {
            return List.of();
        }
    }
}
