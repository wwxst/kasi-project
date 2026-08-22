package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaListItemVO;
import com.kasi.backend.drama.vo.DramaPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .title(drama.getTitle()).originalTitle(drama.getOriginalTitle()).coverUrl(drama.getCoverUrl())
                .language(drama.getLanguage()).dramaType(drama.getDramaType()).remoteShowStatus(drama.getRemoteShowStatus())
                .localStatus(drama.getLocalStatus()).updatedAt(drama.getUpdatedAt()).build();
    }
}
