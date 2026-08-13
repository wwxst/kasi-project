package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.dto.UpdateAdminProfileDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import com.kasi.backend.auth.dto.ChangePasswordDTO;

/**
 * 管理员认证服务。
 */
public interface AdminAuthService {

    AdminLoginVO login(AdminLoginDTO request, String clientIp);

    CurrentAdminVO getCurrentAdmin(Long adminId);

    void changePassword(Long adminId, ChangePasswordDTO request);

    CurrentAdminVO updateProfile(Long adminId, UpdateAdminProfileDTO request);
}
