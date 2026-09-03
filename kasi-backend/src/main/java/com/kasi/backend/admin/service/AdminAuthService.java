package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.dto.UpdateAdminProfileDTO;
import com.kasi.backend.admin.dto.AdminChangePasswordDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员认证服务。
 */
public interface AdminAuthService {

    AdminLoginVO login(AdminLoginDTO request, String clientIp);

    CurrentAdminVO getCurrentAdmin(Long adminId);

    void changePassword(Long adminId, AdminChangePasswordDTO request);

    CurrentAdminVO updateProfile(Long adminId, UpdateAdminProfileDTO request);

    CurrentAdminVO updateAvatar(Long adminId, MultipartFile file);
}
