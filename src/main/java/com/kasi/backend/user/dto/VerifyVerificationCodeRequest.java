package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证验证码请求
 */
@Data
public class VerifyVerificationCodeRequest {

    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    @NotBlank(message = "场景不能为空")
    private String scene;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
