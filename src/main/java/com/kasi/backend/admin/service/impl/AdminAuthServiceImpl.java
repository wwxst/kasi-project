package com.kasi.backend.admin.service.impl;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import com.kasi.backend.auth.dto.ChangePasswordDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.session.AuthSession;
import com.kasi.backend.security.session.SessionMutation;
import com.kasi.backend.security.token.TokenService;
import com.kasi.backend.security.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

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
                        .nickname(admin.getNickname())
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
                .nickname(admin.getNickname())
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
    public void changePassword(Long adminId, ChangePasswordDTO request) {
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
            throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
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
}
