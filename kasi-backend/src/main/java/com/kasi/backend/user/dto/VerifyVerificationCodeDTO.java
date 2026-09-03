package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.PhoneOrEmail;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证验证码请求
 */
@Data
public class VerifyVerificationCodeDTO {

    @NotBlank(message = "手机号或邮箱不能为空")
    @PhoneOrEmail
    private String target;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
