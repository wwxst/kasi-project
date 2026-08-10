package com.kasi.backend.user.service;

import com.kasi.backend.auth.password.PasswordResetTokenService;
import com.kasi.backend.auth.verification.VerificationCodeService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.token.TokenService;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 推广用户认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final PromotionUserMapper promotionUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTokenService passwordResetTokenService;

    @Value("${app.jwt.expiration:7200}")
    private long jwtExpiration;

    @Value("${app.password-reset.token-expiration:600}")
    private long resetTokenExpiration;

    /**
     * 判断目标类型是否为邮箱
     */
    private boolean isEmail(String target) {
        return target != null && target.contains("@");
    }

    /**
     * 用户注册
     */
    @Transactional
    public void register(UserRegisterRequest request) {
        // 密码一致性校验
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
        }

        // 校验验证码
        VerificationScene scene = VerificationScene.REGISTER;
        verificationCodeService.verifyCode(request.getAccount(), scene, request.getVerificationCode());

        // 检查手机号或邮箱是否已注册
        if (isEmail(request.getAccount())) {
            if (promotionUserMapper.findByEmail(request.getAccount()) != null) {
                throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
            }
        } else {
            if (promotionUserMapper.findByMobile(request.getAccount()) != null) {
                throw new BusinessException(ErrorCode.USER_MOBILE_DUPLICATE);
            }
        }

        // 构建用户实体（先用占位编号，插入后根据自增id生成最终编号）
        PromotionUser user = new PromotionUser();
        user.setUserNo("PLACEHOLDER");
        if (isEmail(request.getAccount())) {
            user.setEmail(request.getAccount());
            user.setUsername(request.getAccount());
        } else {
            user.setMobile(request.getAccount());
            user.setUsername(request.getAccount());
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.NORMAL.getCode());
        user.setRegisterSource("MOBILE");

        // 插入后 user.getId() 即为自增主键
        promotionUserMapper.insert(user);

        // 根据自增id生成用户编号并回写
        String userNo = "KS" + String.format("%06d", user.getId());
        String nickname = "用户" + userNo;
        promotionUserMapper.updateUserNo(user.getId(), userNo, nickname);

        log.info("用户注册成功: userNo={}, account={}", userNo, request.getAccount());
    }

    /**
     * 用户登录
     */
    @Transactional
    public UserLoginResponse login(UserLoginRequest request) {
        // 查找用户
        PromotionUser user = promotionUserMapper.findByAccount(request.getAccount());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 检查状态
        if (user.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 生成Token
        String token = tokenService.generateToken(user.getId(), SubjectType.USER, user.getUsername());

        // 更新最后登录信息
        promotionUserMapper.updateLastLogin(user.getId(), LocalDateTime.now(), null);

        // 确定返回的账号标识
        String displayAccount = user.getMobile() != null ? user.getMobile() : user.getEmail();

        return UserLoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(UserLoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .userNo(user.getUserNo())
                        .nickname(user.getNickname())
                        .mobile(user.getMobile())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }

    /**
     * 获取当前用户信息
     */
    public CurrentUserResponse getCurrentUser(Long userId) {
        PromotionUser user = promotionUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return CurrentUserResponse.builder()
                .id(user.getId())
                .userNo(user.getUserNo())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .realName(user.getRealName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 修改登录密码
     */
    @Transactional
    public void changePassword(Long userId, com.kasi.backend.admin.dto.ChangePasswordRequest request) {
        PromotionUser user = promotionUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_OLD_PASSWORD_ERROR);
        }

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_NEW_PASSWORD_SAME);
        }

        // 确认密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
        }

        // 加密并更新
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        promotionUserMapper.updatePassword(userId, encodedPassword);

        log.info("用户 [{}] 修改密码成功", user.getUsername());
    }

    /**
     * 发送忘记密码验证码
     */
    public void sendForgotPasswordCode(SendVerificationCodeRequest request) {
        // 检查用户是否存在
        PromotionUser user = promotionUserMapper.findByAccount(request.getTarget());
        if (user == null) {
            // 不暴露用户是否存在，直接返回成功
            log.info("忘记密码验证码：账号 {} 不存在，静默处理", request.getTarget());
            return;
        }

        verificationCodeService.sendVerificationCode(request.getTarget(), VerificationScene.RESET_PASSWORD);
    }

    /**
     * 验证忘记密码验证码，返回重置Token
     */
    @Transactional
    public VerifyCodeResponse verifyForgotPasswordCode(VerifyVerificationCodeRequest request) {
        // 查找用户
        PromotionUser user = promotionUserMapper.findByAccount(request.getTarget());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证验证码
        verificationCodeService.verifyCode(request.getTarget(), VerificationScene.RESET_PASSWORD, request.getCode());

        // 生成重置Token
        String resetToken = passwordResetTokenService.generateResetToken(user.getId(), "PROMOTION");

        return VerifyCodeResponse.builder()
                .resetToken(resetToken)
                .expiresIn(resetTokenExpiration)
                .build();
    }

    /**
     * 重置密码（使用重置Token）
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 密码一致性校验
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
        }

        // 验证并消费重置Token
        Long userId = passwordResetTokenService.validateAndConsumeToken(request.getResetToken());

        // 更新密码
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        promotionUserMapper.updatePassword(userId, encodedPassword);

        log.info("用户 [ID={}] 通过重置Token重置密码成功", userId);
    }
}
