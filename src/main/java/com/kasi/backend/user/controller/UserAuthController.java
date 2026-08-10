package com.kasi.backend.user.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.context.AuthContextHolder;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 推广用户认证控制器
 */
@RestController
@RequestMapping("/api/user/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody UserRegisterRequest request) {
        userAuthService.register(request);
        return ApiResponse.successMessage("注册成功");
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        UserLoginResponse response = userAuthService.login(request);
        return ApiResponse.success("登录成功", response);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        Long userId = AuthContextHolder.getUserId();
        CurrentUserResponse response = userAuthService.getCurrentUser(userId);
        return ApiResponse.success(response);
    }

    /**
     * 用户退出登录
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.successMessage("退出成功");
    }

    /**
     * 修改登录密码
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody com.kasi.backend.admin.dto.ChangePasswordRequest request) {
        Long userId = AuthContextHolder.getUserId();
        userAuthService.changePassword(userId, request);
        return ApiResponse.successMessage("密码修改成功");
    }

    /**
     * 发送忘记密码验证码
     */
    @PostMapping("/password/forgot/code")
    public ApiResponse<Void> sendForgotPasswordCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        userAuthService.sendForgotPasswordCode(request);
        return ApiResponse.successMessage("验证码已发送");
    }

    /**
     * 验证忘记密码验证码
     */
    @PostMapping("/password/forgot/verify")
    public ApiResponse<VerifyCodeResponse> verifyForgotPasswordCode(
            @Valid @RequestBody VerifyVerificationCodeRequest request) {
        VerifyCodeResponse response = userAuthService.verifyForgotPasswordCode(request);
        return ApiResponse.success("验证成功", response);
    }

    /**
     * 重置密码
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userAuthService.resetPassword(request);
        return ApiResponse.successMessage("密码重置成功");
    }
}
