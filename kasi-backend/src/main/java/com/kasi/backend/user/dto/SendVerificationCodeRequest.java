package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求
 */
@Data
public class SendVerificationCodeRequest {

    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    @NotBlank(message = "场景不能为空")
    private String scene;
}
