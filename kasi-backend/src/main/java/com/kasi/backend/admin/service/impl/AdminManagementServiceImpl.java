package com.kasi.backend.admin.service.impl;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

    private final SysAdminUserMapper sysAdminUserMapper;

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
