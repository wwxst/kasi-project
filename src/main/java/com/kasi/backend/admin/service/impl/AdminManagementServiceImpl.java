package com.kasi.backend.admin.service.impl;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminStatusDTO;
import com.kasi.backend.admin.dto.ResetAdminPasswordDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.AdminManagementService;
import com.kasi.backend.admin.vo.AdminDetailVO;
import com.kasi.backend.admin.vo.AdminListItemVO;
import com.kasi.backend.admin.vo.AdminPageVO;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminManagementServiceImpl implements AdminManagementService {

    private final SysAdminUserMapper sysAdminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    @Override
    @Transactional(readOnly = true)
    public AdminPageVO getPage(AdminPageQueryDTO query) {
        String keyword = normalizeKeyword(query.getKeyword());
        long total = sysAdminUserMapper.countByKeyword(keyword);
        long offset = ((long) query.getPage() - 1) * query.getSize();
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

    @Override
    @Transactional
    public AdminDetailVO update(Long operatorId, Long targetId, UpdateAdminDTO request) {
        SysAdminUser admin = sysAdminUserMapper.findByIdForUpdate(targetId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_MANAGEMENT_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(admin.getIsSuperAdmin())) {
            throw new BusinessException(ErrorCode.ADMIN_SUPER_ADMIN_PROTECTED);
        }
        String mobile = trimToNull(request.getMobile());
        String email = normalizeEmail(request.getEmail());
        checkUniqueIdentifiersForUpdate(targetId, request.getUsername(), mobile, email);
        boolean identifierChanged = !request.getUsername().equals(admin.getUsername())
                || !java.util.Objects.equals(mobile, admin.getMobile())
                || !java.util.Objects.equals(email, admin.getEmail());
        SessionMutation mutation = identifierChanged
                ? sessionService.beginMutation(SubjectType.ADMIN, targetId) : null;

        applyProfile(admin, request.getUsername(), request.getRealName(), mobile, email,
                request.getAvatarUrl(), request.getDepartmentId(), request.getRemark(), operatorId);
        updateProfile(admin);
        registerCompletion(mutation);
        return toDetailVO(sysAdminUserMapper.findById(targetId));
    }

    @Override
    @Transactional
    public void updateStatus(Long operatorId, Long targetId, UpdateAdminStatusDTO request) {
        SysAdminUser admin = getMutableOrdinaryAdmin(targetId);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.ADMIN, targetId);
        if (sysAdminUserMapper.updateStatus(admin.getId(), request.getStatus()) != 1) {
            throw new IllegalStateException("管理员状态更新未生效");
        }
        registerCompletion(mutation);
    }

    @Override
    @Transactional
    public void resetPassword(Long operatorId, Long targetId, ResetAdminPasswordDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_NOT_MATCH);
        }
        SysAdminUser admin = getMutableOrdinaryAdmin(targetId);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.ADMIN, targetId);
        if (sysAdminUserMapper.updatePassword(admin.getId(), passwordEncoder.encode(request.getNewPassword()),
                LocalDateTime.now()) != 1) {
            throw new IllegalStateException("管理员密码重置未生效");
        }
        registerCompletion(mutation);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long targetId) {
        SysAdminUser admin = getMutableOrdinaryAdmin(targetId);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.ADMIN, targetId);
        if (sysAdminUserMapper.deleteOrdinaryById(admin.getId()) != 1) {
            throw new IllegalStateException("管理员删除未生效");
        }
        registerCompletion(mutation);
    }

    private SysAdminUser getMutableOrdinaryAdmin(Long targetId) {
        SysAdminUser admin = sysAdminUserMapper.findByIdForUpdate(targetId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.ADMIN_MANAGEMENT_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(admin.getIsSuperAdmin())) {
            throw new BusinessException(ErrorCode.ADMIN_SUPER_ADMIN_PROTECTED);
        }
        return admin;
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

    private void checkUniqueIdentifiersForUpdate(Long targetId, String username, String mobile, String email) {
        checkOther(sysAdminUserMapper.findByUsername(username), targetId, ErrorCode.ADMIN_USERNAME_DUPLICATE);
        if (mobile != null) {
            checkOther(sysAdminUserMapper.findByMobile(mobile), targetId, ErrorCode.ADMIN_MOBILE_DUPLICATE);
        }
        if (email != null) {
            checkOther(sysAdminUserMapper.findByEmail(email), targetId, ErrorCode.ADMIN_EMAIL_DUPLICATE);
        }
    }

    private void checkOther(SysAdminUser existing, Long targetId, ErrorCode errorCode) {
        if (existing != null && !targetId.equals(existing.getId())) {
            throw new BusinessException(errorCode);
        }
    }

    private void applyProfile(SysAdminUser admin, String username, String realName,
                              String mobile, String email, String avatarUrl,
                              Long departmentId, String remark, Long operatorId) {
        admin.setUsername(username);
        admin.setRealName(realName);
        admin.setMobile(mobile);
        admin.setEmail(email);
        admin.setAvatarUrl(avatarUrl);
        admin.setDepartmentId(departmentId);
        admin.setRemark(remark);
        admin.setUpdatedBy(operatorId);
    }

    private void updateProfile(SysAdminUser admin) {
        try {
            if (sysAdminUserMapper.updateProfile(admin) != 1) {
                throw new IllegalStateException("管理员资料更新未生效");
            }
        } catch (DuplicateKeyException exception) {
            checkUniqueIdentifiersForUpdate(admin.getId(), admin.getUsername(), admin.getMobile(), admin.getEmail());
            throw exception;
        }
    }

    private void registerCompletion(SessionMutation mutation) {
        if (mutation == null) {
            return;
        }
        sessionService.registerMutationCompletion(mutation);
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
