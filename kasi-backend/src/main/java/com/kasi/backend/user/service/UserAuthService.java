package com.kasi.backend.user.service;

import com.kasi.backend.auth.dto.ChangePasswordDTO;
import com.kasi.backend.user.dto.ResetPasswordDTO;
import com.kasi.backend.user.dto.SendVerificationCodeDTO;
import com.kasi.backend.user.dto.UserLoginDTO;
import com.kasi.backend.user.dto.UserRegisterDTO;
import com.kasi.backend.user.dto.VerifyVerificationCodeDTO;
import com.kasi.backend.user.vo.CurrentUserVO;
import com.kasi.backend.user.vo.UserLoginVO;
import com.kasi.backend.user.vo.VerifyCodeVO;

/**
 * 推广用户认证服务。
 */
public interface UserAuthService {

    void register(UserRegisterDTO request);

    void sendRegisterCode(SendVerificationCodeDTO request);

    UserLoginVO login(UserLoginDTO request, String clientIp);

    CurrentUserVO getCurrentUser(Long userId);

    void changePassword(Long userId, ChangePasswordDTO request);

    void sendForgotPasswordCode(SendVerificationCodeDTO request);

    VerifyCodeVO verifyForgotPasswordCode(VerifyVerificationCodeDTO request);

    void resetPassword(ResetPasswordDTO request);
}
