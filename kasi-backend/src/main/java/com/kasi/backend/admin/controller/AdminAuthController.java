package com.kasi.backend.admin.controller;

import com.kasi.backend.admin.dto.AdminLoginRequest;
import com.kasi.backend.admin.dto.AdminLoginResponse;
import com.kasi.backend.admin.dto.ChangePasswordRequest;
import com.kasi.backend.admin.dto.CurrentAdminResponse;
import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员认证控制器
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminAuthService.login(request);
        return ApiResponse.success("登录成功", response);
    }

    /**
     * 获取当前管理员信息
     */
    @GetMapping("/me")
    public ApiResponse<CurrentAdminResponse> me() {
        Long adminId = AuthContextHolder.getAdminId();
        CurrentAdminResponse response = adminAuthService.getCurrentAdmin(adminId);
        return ApiResponse.success(response);
    }

    /**
     * 管理员退出登录
     * <p>
     * 当前使用无状态JWT，退出由客户端清除Token即可，
     * 服务端仅返回成功确认。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.successMessage("退出成功");
    }

    /**
     * 修改登录密码
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long adminId = AuthContextHolder.getAdminId();
        adminAuthService.changePassword(adminId, request);
        return ApiResponse.successMessage("密码修改成功");
    }
}
