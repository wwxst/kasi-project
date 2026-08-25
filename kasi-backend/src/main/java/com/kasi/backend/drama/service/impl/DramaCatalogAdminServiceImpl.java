package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.DramaCatalogAdminService;
import com.kasi.backend.drama.vo.DramaContentVO;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaListItemVO;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class DramaCatalogAdminServiceImpl implements DramaCatalogAdminService {
    private final ProviderDramaMapper dramaMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final tools.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    @Transactional(readOnly = true)
    public DramaPageVO getPage(DramaPageQueryDTO query) {
        Long connectionId = resolveConnectionId(query.getProviderId());
        String title = trimToNull(query.getTitle());
        String language = trimToNull(query.getLanguage());
        String remoteStatus = trimToNull(query.getRemoteShowStatus());
        long total = dramaMapper.count(connectionId, title, language, remoteStatus, query.getLocalStatus());
        int offset = (query.getPage() - 1) * query.getSize();
        List<DramaListItemVO> list = dramaMapper.page(connectionId, title, language, remoteStatus,
                        query.getLocalStatus(), offset, query.getSize()).stream()
                .map(this::toListItem).toList();
        return DramaPageVO.builder().list(list).page(query.getPage()).size(query.getSize()).total(total).build();
    }

    @Override
    @Transactional(readOnly = true)
    public DramaDetailVO getById(Long id) {
        ProviderDrama drama = requireDrama(id);
        List<DramaContentVO> contents = dramaMapper.findContents(id).stream().map(this::toContent).toList();
        return toDetail(drama, contents);
    }

    @Override
    @Transactional
    public DramaDetailVO updateLocalStatus(Long id, DramaLocalStatus localStatus) {
        if (localStatus != DramaLocalStatus.PUBLISHED && localStatus != DramaLocalStatus.OFFLINE) {
            throw new BusinessException(ErrorCode.DRAMA_LOCAL_STATUS_INVALID);
        }
        requireDrama(id);
        if (dramaMapper.updateLocalStatus(id, localStatus) != 1) {
            throw new BusinessException(ErrorCode.DRAMA_NOT_FOUND);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public DramaDetailVO updatePromotionMetadata(Long id, List<PromotionCommissionScope> commissionScopes,
                                                  String promotionDescription) {
        requireDrama(id);
        String normalizedScopes = commissionScopes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        String normalizedDescription = trimToNull(promotionDescription);
        if (dramaMapper.updatePromotionMetadata(id,
                normalizedScopes.isBlank() ? null : normalizedScopes, normalizedDescription) != 1) {
            throw new BusinessException(ErrorCode.DRAMA_NOT_FOUND);
        }
        return getById(id);
    }

    private Long resolveConnectionId(Long providerId) {
        if (providerId == null) return null;
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        return connection.getId();
    }

    private ProviderDrama requireDrama(Long id) {
        ProviderDrama drama = dramaMapper.findById(id);
        if (drama == null) throw new BusinessException(ErrorCode.DRAMA_NOT_FOUND);
        return drama;
    }

    private DramaListItemVO toListItem(ProviderDrama drama) {
        return DramaListItemVO.builder().id(drama.getId()).externalDramaId(drama.getExternalDramaId())
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).titleZh(drama.getTitleZh())
                .description(drama.getDescription()).coverUrl(drama.getCoverUrl()).labelNames(parseLabels(drama.getLabelNames()))
                .categoryName(drama.getCategoryName()).language(drama.getLanguage()).remoteRank(drama.getRemoteRank())
                .dramaType(drama.getDramaType()).novelType(drama.getNovelType()).novelSubType(drama.getNovelSubType())
                .commissionScopes(parseScopes(drama.getCommissionScope()))
                .promotionDescription(drama.getPromotionDescription())
                .remoteShowStatus(drama.getRemoteShowStatus()).localStatus(drama.getLocalStatus())
                .remoteCreatedAt(drama.getRemoteCreatedAt()).remoteUpdatedAt(drama.getRemoteUpdatedAt()).lastSeenAt(drama.getLastSeenAt())
                .updatedAt(drama.getUpdatedAt()).build();
    }

    private DramaDetailVO toDetail(ProviderDrama drama, List<DramaContentVO> contents) {
        return DramaDetailVO.builder().id(drama.getId()).externalDramaId(drama.getExternalDramaId())
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).titleZh(drama.getTitleZh())
                .description(drama.getDescription()).coverUrl(drama.getCoverUrl()).labelNames(parseLabels(drama.getLabelNames()))
                .categoryName(drama.getCategoryName()).language(drama.getLanguage()).remoteRank(drama.getRemoteRank())
                .dramaType(drama.getDramaType()).novelType(drama.getNovelType()).novelSubType(drama.getNovelSubType())
                .commissionScopes(parseScopes(drama.getCommissionScope()))
                .promotionDescription(drama.getPromotionDescription())
                .remoteShowStatus(drama.getRemoteShowStatus()).localStatus(drama.getLocalStatus())
                .remoteCreatedAt(drama.getRemoteCreatedAt()).remoteUpdatedAt(drama.getRemoteUpdatedAt()).lastSeenAt(drama.getLastSeenAt())
                .createdAt(drama.getCreatedAt()).updatedAt(drama.getUpdatedAt()).contents(contents).build();
    }

    private DramaContentVO toContent(ProviderDramaContent content) {
        return DramaContentVO.builder().id(content.getId()).externalContentId(content.getExternalContentId())
                .sequenceNo(content.getSequenceNo()).title(content.getTitle()).free(Boolean.TRUE.equals(content.getFree()))
                .durationSeconds(content.getDurationSeconds()).remoteUpdatedAt(content.getRemoteUpdatedAt()).build();
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private List<PromotionCommissionScope> parseScopes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .map(PromotionCommissionScope::valueOf)
                .toList();
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
