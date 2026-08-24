package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.service.MediaAccountOwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaAccountOwnershipServiceImpl implements MediaAccountOwnershipService {
    private final PromotionMediaAccountMapper mediaMapper;

    @Override
    public boolean hasBoundAccount(Long userId) {
        return !mediaMapper.findByUserId(userId).isEmpty();
    }
}
