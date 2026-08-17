package com.kasi.backend.user.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.generator.UserNumberGenerator;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.PromotionUserCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionUserCreationServiceImpl implements PromotionUserCreationService {

    private static final int MAX_ATTEMPTS = 3;

    private final PromotionUserMapper promotionUserMapper;
    private final UserNumberGenerator userNumberGenerator;

    @Transactional
    @Override
    public void create(PromotionUser user) {
        boolean defaultNickname = user.getNickname() == null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            user.setId(null);
            user.setUserNo(userNumberGenerator.generate());
            if (defaultNickname) {
                user.setNickname("用户" + user.getUserNo());
            }
            try {
                if (promotionUserMapper.insert(user) != 1) {
                    throw new IllegalStateException("推广用户新增未生效");
                }
                return;
            } catch (DuplicateKeyException exception) {
                throwIfContactConflict(user);
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("连续3次生成推广用户编号均发生冲突", exception);
                }
            }
        }
        throw new IllegalStateException("推广用户创建流程未完成");
    }

    private void throwIfContactConflict(PromotionUser user) {
        if (user.getMobile() != null && promotionUserMapper.findByMobile(user.getMobile()) != null) {
            throw new BusinessException(ErrorCode.USER_MOBILE_DUPLICATE);
        }
        if (user.getEmail() != null && promotionUserMapper.findByEmail(user.getEmail()) != null) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }
    }
}
