package com.kasi.backend.admin.service.impl;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.dto.UpdateAdminProfileDTO;
import com.kasi.backend.admin.dto.AdminChangePasswordDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.entity.AuthSession;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.security.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * 管理员认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final SysAdminUserMapper sysAdminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final SessionService sessionService;

    @Value("${app.jwt.expiration:7200}")
    private long jwtExpiration;

    /**
     * 管理员登录
     */
    @Transactional
    @Override
    public AdminLoginVO login(AdminLoginDTO request, String clientIp) {
        String account = request.getAccount().trim();
        if (account.contains("@")) {
            account = account.toLowerCase(Locale.ROOT);
        }
        // 查找管理员
        SysAdminUser admin = sysAdminUserMapper.findByAccountForUpdate(account);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_ERROR);
        }

        // 检查状态
        if (admin.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException(ErrorCode.ADMIN_DISABLED);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_ERROR);
        }

        // 生成Token
        AuthSession session = sessionService.createSession(SubjectType.ADMIN, admin.getId());
        String token = tokenService.generateToken(
                admin.getId(), SubjectType.ADMIN, admin.getUsername(),
                session.jti(), session.sessionVersion());

        // 更新最后登录信息
        sysAdminUserMapper.updateLastLogin(admin.getId(), LocalDateTime.now(), clientIp);

        // 构建响应
        return AdminLoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .admin(AdminLoginVO.AdminInfo.builder()
                        .id(admin.getId())
                        .username(admin.getUsername())
                        .realName(admin.getRealName())
                        .mobile(admin.getMobile())
                        .email(admin.getEmail())
                        .avatarUrl(admin.getAvatarUrl())
                        .isSuperAdmin(admin.getIsSuperAdmin())
                        .build())
                .build();
    }

    /**
     * 获取当前管理员信息
     */
    @Override
    public CurrentAdminVO getCurrentAdmin(Long adminId) {
        SysAdminUser admin = sysAdminUserMapper.findById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        return CurrentAdminVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .realName(admin.getRealName())
                .mobile(admin.getMobile())
                .email(admin.getEmail())
                .avatarUrl(admin.getAvatarUrl())
                .status(admin.getStatus())
                .isSuperAdmin(admin.getIsSuperAdmin())
                .lastLoginAt(admin.getLastLoginAt())
                .lastLoginIp(admin.getLastLoginIp())
                .createdAt(admin.getCreatedAt())
                .build();
    }

    /**
     * 修改管理员密码
     */
    @Transactional
    @Override
    public void changePassword(Long adminId, AdminChangePasswordDTO request) {
        SysAdminUser admin = sysAdminUserMapper.findByIdForUpdate(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_OLD_PASSWORD_ERROR);
        }

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_NEW_PASSWORD_SAME);
        }

        // 确认密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_NOT_MATCH);
        }

        // 加密并更新
        SessionMutation mutation = sessionService.beginMutation(SubjectType.ADMIN, adminId);
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        int updated = sysAdminUserMapper.updatePassword(adminId, encodedPassword, LocalDateTime.now());
        if (updated != 1) {
            throw new IllegalStateException("管理员密码更新未生效");
        }
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sessionService.completeMutation(mutation);
                    }
                });

        log.info("管理员 [{}] 修改密码成功", admin.getUsername());
    }

    @Override
    @Transactional
    public CurrentAdminVO updateProfile(Long adminId, UpdateAdminProfileDTO request) {
        SysAdminUser admin = sysAdminUserMapper.findByIdForUpdate(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }
        String mobile = trimToNull(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        ensureProfileUnique(adminId, request.getUsername(), mobile, email);
        boolean identifierChanged = !request.getUsername().equals(admin.getUsername())
                || !Objects.equals(mobile, admin.getMobile())
                || !Objects.equals(email, admin.getEmail());
        SessionMutation mutation = identifierChanged
                ? sessionService.beginMutation(SubjectType.ADMIN, adminId) : null;
        admin.setUsername(request.getUsername());
        admin.setRealName(request.getRealName());
        admin.setMobile(mobile);
        admin.setEmail(email);
        admin.setAvatarUrl(request.getAvatarUrl());
        admin.setUpdatedBy(adminId);
        if (sysAdminUserMapper.updateProfile(admin) != 1) {
            throw new IllegalStateException("管理员资料更新未生效");
        }
        if (mutation != null) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sessionService.completeMutation(mutation);
                        }
                    });
        }
        return getCurrentAdmin(adminId);
    }

    private void ensureProfileUnique(Long adminId, String username, String mobile, String email) {
        ensureOther(sysAdminUserMapper.findByUsername(username), adminId, ErrorCode.ADMIN_USERNAME_DUPLICATE);
        if (mobile != null) {
            ensureOther(sysAdminUserMapper.findByMobile(mobile), adminId, ErrorCode.ADMIN_MOBILE_DUPLICATE);
        }
        if (email != null) {
            ensureOther(sysAdminUserMapper.findByEmail(email), adminId, ErrorCode.ADMIN_EMAIL_DUPLICATE);
        }
    }

    private void ensureOther(SysAdminUser existing, Long adminId, ErrorCode errorCode) {
        if (existing != null && !adminId.equals(existing.getId())) {
            throw new BusinessException(errorCode);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeEmail(String email) {
        String value = trimToNull(email);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
