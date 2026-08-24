package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaListItemVO;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPromotionDramaServiceImpl implements UserPromotionDramaService {
    private final ProviderDramaMapper dramaMapper;

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
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).description(drama.getDescription())
                .coverUrl(drama.getCoverUrl()).language(drama.getLanguage()).dramaType(drama.getDramaType())
                .commissionScopes(parseScopes(drama.getCommissionScope()))
                .promotionDescription(drama.getPromotionDescription())
                .remoteShowStatus(drama.getRemoteShowStatus())
                .localStatus(drama.getLocalStatus()).remoteUpdatedAt(drama.getRemoteUpdatedAt())
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
}
