package com.kasi.backend.admin.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员登录请求
 */
@Data
public class AdminLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Utf8ByteLength
    private String password;
}
