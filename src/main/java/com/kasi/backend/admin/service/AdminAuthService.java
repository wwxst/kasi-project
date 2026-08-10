package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.AdminLoginRequest;
import com.kasi.backend.admin.dto.AdminLoginResponse;
import com.kasi.backend.admin.dto.ChangePasswordRequest;
import com.kasi.backend.admin.dto.CurrentAdminResponse;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.token.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final SysAdminUserMapper sysAdminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Value("${app.jwt.expiration:7200}")
    private long jwtExpiration;

    /**
     * 管理员登录
     */
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 查找管理员
        SysAdminUser admin = sysAdminUserMapper.findByAccount(request.getAccount());
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
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
        String token = tokenService.generateToken(admin.getId(), SubjectType.ADMIN, admin.getUsername());

        // 更新最后登录信息
        sysAdminUserMapper.updateLastLogin(admin.getId(), LocalDateTime.now(), null);

        // 构建响应
        return AdminLoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .admin(AdminLoginResponse.AdminInfo.builder()
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
    public CurrentAdminResponse getCurrentAdmin(Long adminId) {
        SysAdminUser admin = sysAdminUserMapper.findById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_FOUND);
        }

        return CurrentAdminResponse.builder()
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
    public void changePassword(Long adminId, ChangePasswordRequest request) {
        SysAdminUser admin = sysAdminUserMapper.findById(adminId);
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
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        sysAdminUserMapper.updatePassword(adminId, encodedPassword, LocalDateTime.now());

        log.info("管理员 [{}] 修改密码成功", admin.getUsername());
    }
}
