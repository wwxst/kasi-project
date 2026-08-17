package com.kasi.backend.user.service.impl;

import com.kasi.backend.auth.service.PasswordResetTokenService;
import com.kasi.backend.auth.entity.PasswordResetTokenReservation;
import com.kasi.backend.auth.dto.ChangePasswordDTO;
import com.kasi.backend.auth.service.VerificationCodeService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.entity.AuthSession;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.vo.*;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.PromotionUserCreationService;
import com.kasi.backend.user.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 推广用户认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final PromotionUserMapper promotionUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final SessionService sessionService;
    private final PromotionUserCreationService promotionUserCreationService;

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

    private String normalizeAccount(String account) {
        String normalized = account.trim();
        return isEmail(normalized) ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    /**
     * 用户注册
     */
    @Transactional
    @Override
    public void register(UserRegisterDTO request) {
        String account = normalizeAccount(request.getAccount());
        // 密码一致性校验
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
        }

        // 校验验证码
        VerificationScene scene = VerificationScene.REGISTER;
        verificationCodeService.verifyCode(account, scene, request.getVerificationCode());

        // 检查手机号或邮箱是否已注册
        if (isEmail(account)) {
            if (promotionUserMapper.findByEmail(account) != null) {
                throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
            }
        } else {
            if (promotionUserMapper.findByMobile(account) != null) {
                throw new BusinessException(ErrorCode.USER_MOBILE_DUPLICATE);
            }
        }

        PromotionUser user = new PromotionUser();
        if (isEmail(account)) {
            user.setEmail(account);
        } else {
            user.setMobile(account);
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.NORMAL.getCode());
        user.setRegisterSource(isEmail(account) ? "EMAIL" : "MOBILE");

        promotionUserCreationService.create(user);
        String userNo = user.getUserNo();
        log.info("用户注册成功: userNo={}, account={}", userNo, account);
    }

    @Override
    public void sendRegisterCode(SendVerificationCodeDTO request) {
        verificationCodeService.sendVerificationCode(
                normalizeAccount(request.getTarget()), VerificationScene.REGISTER);
    }

    /**
     * 用户登录
     */
    @Transactional
    @Override
    public UserLoginVO login(UserLoginDTO request, String clientIp) {
        String account = normalizeAccount(request.getAccount());
        // 查找用户
        PromotionUser user = promotionUserMapper.findByAccountForUpdate(account);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
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
        AuthSession session = sessionService.createSession(SubjectType.USER, user.getId());
        String token = tokenService.generateToken(
                user.getId(), SubjectType.USER, account,
                session.jti(), session.sessionVersion());

        // 更新最后登录信息
        promotionUserMapper.updateLastLogin(user.getId(), LocalDateTime.now(), clientIp);

        return UserLoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(UserLoginVO.UserInfo.builder()
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
    @Override
    public CurrentUserVO getCurrentUser(Long userId) {
        PromotionUser user = promotionUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return CurrentUserVO.builder()
                .id(user.getId())
                .userNo(user.getUserNo())
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
    @Override
    public void changePassword(Long userId, ChangePasswordDTO request) {
        PromotionUser user = promotionUserMapper.findByIdForUpdate(userId);
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
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, userId);
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        int updated = promotionUserMapper.updatePassword(userId, encodedPassword);
        if (updated != 1) {
            throw new IllegalStateException("用户密码更新未生效");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionService.completeMutation(mutation);
            }
        });

        log.info("用户 [ID={}] 修改密码成功", userId);
    }

    /**
     * 发送忘记密码验证码
     */
    @Override
    public void sendForgotPasswordCode(SendVerificationCodeDTO request) {
        String target = normalizeAccount(request.getTarget());
        // 检查用户是否存在
        PromotionUser user = promotionUserMapper.findByAccount(target);
        if (user == null) {
            verificationCodeService.reserveVerificationCode(target, VerificationScene.RESET_PASSWORD);
            return;
        }

        verificationCodeService.sendVerificationCode(target, VerificationScene.RESET_PASSWORD);
    }

    /**
     * 验证忘记密码验证码，返回重置Token
     */
    @Transactional
    @Override
    public VerifyCodeVO verifyForgotPasswordCode(VerifyVerificationCodeDTO request) {
        String target = normalizeAccount(request.getTarget());
        // 验证验证码
        verificationCodeService.verifyCode(target, VerificationScene.RESET_PASSWORD, request.getCode());

        PromotionUser user = promotionUserMapper.findByAccount(target);
        if (user == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
        }

        // 生成重置Token
        String resetToken = passwordResetTokenService.generateResetToken(user.getId(), SubjectType.USER);

        return VerifyCodeVO.builder()
                .resetToken(resetToken)
                .expiresIn(resetTokenExpiration)
                .build();
    }

    /**
     * 重置密码（使用重置Token）
     */
    @Transactional
    @Override
    public void resetPassword(ResetPasswordDTO request) {
        // 密码一致性校验
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
        }

        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        PasswordResetTokenReservation reservation = passwordResetTokenService.reserveToken(request.getResetToken());
        if (reservation.subjectType() != SubjectType.USER) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        PromotionUser user = promotionUserMapper.findByIdForUpdate(reservation.userId());
        if (user == null) {
            passwordResetTokenService.restoreReady(reservation);
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, reservation.userId());
        int updated = promotionUserMapper.updatePassword(reservation.userId(), encodedPassword);
        if (updated != 1) {
            passwordResetTokenService.restoreReady(reservation);
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionService.completeMutation(mutation);
                passwordResetTokenService.completeToken(reservation);
            }
        });

        log.info("用户 [ID={}] 通过重置Token重置密码成功", reservation.userId());
    }
}
