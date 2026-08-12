package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.PhoneOrEmail;
import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterDTO {

    @NotBlank(message = "手机号或邮箱不能为空")
    @PhoneOrEmail
    private String account;

    @NotBlank(message = "验证码不能为空")
    private String verificationCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码长度不能少于8位")
    @Utf8ByteLength
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Utf8ByteLength
    private String confirmPassword;
}
