package com.kasi.backend.user.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.auth.dto.ChangePasswordDTO;
import com.kasi.backend.security.context.AuthContextHolder;
import com.kasi.backend.security.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.vo.*;
import com.kasi.backend.user.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 推广用户认证控制器
 */
@RestController
@RequestMapping("/api/user/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final SessionService sessionService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody UserRegisterDTO request) {
        userAuthService.register(request);
        return ApiResponse.successMessage("注册成功");
    }

    /** 注册验证码场景由服务端固定为 REGISTER。 */
    @PostMapping("/register/code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendVerificationCodeDTO request) {
        userAuthService.sendRegisterCode(request);
        return ApiResponse.successMessage("验证码已发送");
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody UserLoginDTO request,
                                                 HttpServletRequest httpRequest) {
        UserLoginVO response = userAuthService.login(request, httpRequest.getRemoteAddr());
        return ApiResponse.success("登录成功", response);
    }

    @PostMapping("/login/code")
    public ApiResponse<Void> sendLoginCode(@Valid @RequestBody SendVerificationCodeDTO request) {
        userAuthService.sendLoginCode(request);
        return ApiResponse.successMessage("验证码已发送");
    }

    @PostMapping("/login/code/verify")
    public ApiResponse<UserLoginVO> loginWithCode(@Valid @RequestBody VerifyVerificationCodeDTO request,
                                                   HttpServletRequest httpRequest) {
        return ApiResponse.success("登录成功",
                userAuthService.loginWithCode(request, httpRequest.getRemoteAddr()));
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me() {
        Long userId = AuthContextHolder.getUserId();
        CurrentUserVO response = userAuthService.getCurrentUser(userId);
        return ApiResponse.success(response);
    }

    @PutMapping("/profile")
    public ApiResponse<CurrentUserVO> updateProfile(@Valid @RequestBody UpdateUserProfileDTO request) {
        return ApiResponse.success(userAuthService.updateProfile(AuthContextHolder.getUserId(), request));
    }

    @PutMapping("/avatar")
    public ApiResponse<CurrentUserVO> updateAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(userAuthService.updateAvatar(AuthContextHolder.getUserId(), file));
    }

    /**
     * 用户退出登录
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
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordDTO request) {
        Long userId = AuthContextHolder.getUserId();
        userAuthService.changePassword(userId, request);
        return ApiResponse.successMessage("密码修改成功");
    }

    /**
     * 发送忘记密码验证码
     */
    @PostMapping("/password/forgot/code")
    public ApiResponse<Void> sendForgotPasswordCode(@Valid @RequestBody SendVerificationCodeDTO request) {
        userAuthService.sendForgotPasswordCode(request);
        return ApiResponse.successMessage("验证码已发送");
    }

    /**
     * 验证忘记密码验证码
     */
    @PostMapping("/password/forgot/verify")
    public ApiResponse<VerifyCodeVO> verifyForgotPasswordCode(
            @Valid @RequestBody VerifyVerificationCodeDTO request) {
        VerifyCodeVO response = userAuthService.verifyForgotPasswordCode(request);
        return ApiResponse.success("验证成功", response);
    }

    /**
     * 重置密码
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO request) {
        userAuthService.resetPassword(request);
        return ApiResponse.successMessage("密码重置成功");
    }
}
