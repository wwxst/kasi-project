package com.kasi.backend.admin.service.impl;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.AdminManagementService;
import com.kasi.backend.admin.vo.AdminDetailVO;
import com.kasi.backend.admin.vo.AdminListItemVO;
import com.kasi.backend.admin.vo.AdminPageVO;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

    private final SysAdminUserMapper sysAdminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public AdminPageVO getPage(AdminPageQueryDTO query) {
        String keyword = normalizeKeyword(query.getKeyword());
        long total = sysAdminUserMapper.countByKeyword(keyword);
        int offset = (query.getPage() - 1) * query.getSize();
        List<AdminListItemVO> list = sysAdminUserMapper.findPage(keyword, offset, query.getSize()).stream()
                .map(this::toListItemVO)
                .toList();
        return AdminPageVO.builder()
                .list(list)
                .page(query.getPage())
                .size(query.getSize())
                .total(total)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDetailVO getById(Long id) {
        SysAdminUser admin = sysAdminUserMapper.findById(id);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_MANAGEMENT_NOT_FOUND);
        }
        return toDetailVO(admin);
    }

    @Override
    @Transactional
    public AdminDetailVO create(Long operatorId, CreateAdminDTO request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_NOT_MATCH);
        }
        String mobile = trimToNull(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        checkUniqueIdentifiers(request.getUsername(), mobile, email);

        SysAdminUser admin = new SysAdminUser();
        admin.setUsername(request.getUsername());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRealName(request.getRealName());
        admin.setMobile(mobile);
        admin.setEmail(email);
        admin.setAvatarUrl(request.getAvatarUrl());
        admin.setDepartmentId(request.getDepartmentId());
        admin.setRemark(request.getRemark());
        admin.setStatus(1);
        admin.setIsSuperAdmin(0);
        admin.setCreatedBy(operatorId);
        admin.setUpdatedBy(operatorId);
        try {
            if (sysAdminUserMapper.insert(admin) != 1) {
                throw new IllegalStateException("管理员新增未生效");
            }
        } catch (DuplicateKeyException exception) {
            checkUniqueIdentifiers(request.getUsername(), mobile, email);
            throw exception;
        }
        return toDetailVO(sysAdminUserMapper.findById(admin.getId()));
    }

    private void checkUniqueIdentifiers(String username, String mobile, String email) {
        if (sysAdminUserMapper.findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.ADMIN_USERNAME_DUPLICATE);
        }
        if (mobile != null && sysAdminUserMapper.findByMobile(mobile) != null) {
            throw new BusinessException(ErrorCode.ADMIN_MOBILE_DUPLICATE);
        }
        if (email != null && sysAdminUserMapper.findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.ADMIN_EMAIL_DUPLICATE);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    private AdminListItemVO toListItemVO(SysAdminUser admin) {
        return AdminListItemVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .realName(admin.getRealName())
                .mobile(admin.getMobile())
                .email(admin.getEmail())
                .avatarUrl(admin.getAvatarUrl())
                .departmentId(admin.getDepartmentId())
                .status(admin.getStatus())
                .isSuperAdmin(admin.getIsSuperAdmin())
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .build();
    }

    private AdminDetailVO toDetailVO(SysAdminUser admin) {
        return AdminDetailVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .realName(admin.getRealName())
                .mobile(admin.getMobile())
                .email(admin.getEmail())
                .avatarUrl(admin.getAvatarUrl())
                .departmentId(admin.getDepartmentId())
                .status(admin.getStatus())
                .isSuperAdmin(admin.getIsSuperAdmin())
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .lastLoginIp(admin.getLastLoginIp())
                .passwordChangedAt(admin.getPasswordChangedAt())
                .remark(admin.getRemark())
                .createdBy(admin.getCreatedBy())
                .updatedBy(admin.getUpdatedBy())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }
}
