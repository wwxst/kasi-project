package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.PhoneOrEmail;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求
 */
@Data
public class SendVerificationCodeDTO {

    @NotBlank(message = "手机号或邮箱不能为空")
    @PhoneOrEmail
    private String target;

}
