package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.dto.AdminPromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.service.PromotionLinkAdminService;
import com.kasi.backend.promotion.vo.AdminPromotionLinkPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionLinkAdminServiceImpl implements PromotionLinkAdminService {
    private final PromotionLinkMapper linkMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminPromotionLinkPageVO getPage(AdminPromotionLinkPageQueryDTO query) {
        long total = linkMapper.countAdminPage(query.getUserNo(), query.getProviderId(),
                query.getExternalCode(), query.getTrackingNo());
        return AdminPromotionLinkPageVO.builder()
                .list(linkMapper.findAdminPage(query.getUserNo(), query.getProviderId(),
                        query.getExternalCode(), query.getTrackingNo(),
                        (query.getPage() - 1) * query.getSize(), query.getSize()))
                .page(query.getPage()).size(query.getSize()).total(total).build();
    }
}
