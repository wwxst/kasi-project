package com.kasi.backend.admin.controller;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.dto.UpdateAdminProfileDTO;
import com.kasi.backend.admin.dto.AdminChangePasswordDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.context.AuthContextHolder;
import com.kasi.backend.security.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final SessionService sessionService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO request,
                                                  HttpServletRequest httpRequest) {
        AdminLoginVO response = adminAuthService.login(request, httpRequest.getRemoteAddr());
        return ApiResponse.success("登录成功", response);
    }

    /**
     * 获取当前管理员信息
     */
    @GetMapping("/me")
    public ApiResponse<CurrentAdminVO> me() {
        Long adminId = AuthContextHolder.getAdminId();
        CurrentAdminVO response = adminAuthService.getCurrentAdmin(adminId);
        return ApiResponse.success(response);
    }

    /**
     * 管理员退出登录
     * <p>
     * 删除 Redis 中的单会话状态，仅撤销当前设备 Token。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        sessionService.revokeSession(AuthContextHolder.get().getJti());
        return ApiResponse.successMessage("退出成功");
    }

    /**
     * 修改登录密码
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminChangePasswordDTO request) {
        Long adminId = AuthContextHolder.getAdminId();
        adminAuthService.changePassword(adminId, request);
        return ApiResponse.successMessage("密码修改成功");
    }

    @PutMapping("/profile")
    public ApiResponse<CurrentAdminVO> updateProfile(@Valid @RequestBody UpdateAdminProfileDTO request) {
        return ApiResponse.success(adminAuthService.updateProfile(AuthContextHolder.getAdminId(), request));
    }
}
