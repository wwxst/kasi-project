package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.service.DramaResourceCacheService;
import com.kasi.backend.drama.service.DramaMediaUrlValidator;
import com.kasi.backend.drama.vo.DramaListItemVO;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.drama.vo.DramaContentVO;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaContentResourceVO;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.FreeContentProviderAdapter;
import com.kasi.backend.provider.spi.FreeContentResult;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
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
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final DramaResourceCacheService resourceCacheService;
    private final DramaMediaUrlValidator mediaUrlValidator;
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
    public List<DramaContentResourceVO> getFreeContent(Long id, boolean refresh) {
        ProviderDrama drama = requirePublishedDrama(id);
        ShortDramaConnection connection = connectionMapper.findById(drama.getConnectionId());
        if (connection == null) throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        ProviderRuntimeConnection runtime = runtimeService.resolve(connection.getProviderId(),
                ProviderCapability.FREE_CONTENT_PREVIEW);
        if (!runtime.connectionId().equals(drama.getConnectionId())
                || !(runtime.adapter() instanceof FreeContentProviderAdapter adapter)) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        if (refresh) resourceCacheService.evict(id);
        List<FreeContentResult> remote = resourceCacheService.get(id,
                () -> fetchRemote(adapter, runtime, drama));
        List<ProviderDramaContent> local = dramaMapper.findContents(id);
        boolean[] used = new boolean[remote.size()];
        return local.stream().map(content -> {
            String url = null;
            if (Boolean.TRUE.equals(content.getFree())) {
                int match = findRemote(remote, used, content.getTitle());
                if (match >= 0 && mediaUrlValidator.isAllowed(remote.get(match).contentUrl())) {
                    used[match] = true;
                    url = remote.get(match).contentUrl();
                }
            }
            return DramaContentResourceVO.builder().id(content.getId()).sequenceNo(content.getSequenceNo())
                    .title(content.getTitle()).free(Boolean.TRUE.equals(content.getFree()))
                    .playUrl(url).downloadUrl(url).build();
        }).toList();
    }

    private List<FreeContentResult> fetchRemote(FreeContentProviderAdapter adapter,
                                                ProviderRuntimeConnection runtime,
                                                ProviderDrama drama) {
        try {
            return adapter.fetchFreeContent(runtime.secret(), drama.getExternalDramaId());
        } catch (ProviderTransientException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_UNAVAILABLE);
        } catch (ProviderRemoteRejectedException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_REMOTE_REJECTED);
        }
    }

    private ProviderDrama requirePublishedDrama(Long id) {
        ProviderDrama drama = dramaMapper.findById(id);
        if (drama == null || drama.getLocalStatus() != DramaLocalStatus.PUBLISHED
                || !"1".equals(drama.getRemoteShowStatus())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        return drama;
    }

    private int findRemote(List<FreeContentResult> remote, boolean[] used, String title) {
        String normalized = normalizeTitle(title);
        if (normalized != null) {
            for (int i = 0; i < remote.size(); i++) {
                if (!used[i] && normalized.equals(normalizeTitle(remote.get(i).chapterName()))) return i;
            }
        }
        for (int i = 0; i < remote.size(); i++) {
            if (!used[i]) return i;
        }
        return -1;
    }

    private String normalizeTitle(String title) {
        return title == null || title.isBlank() ? null : title.trim().toLowerCase(java.util.Locale.ROOT);
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
