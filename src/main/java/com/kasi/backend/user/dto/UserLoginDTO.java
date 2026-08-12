package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 */
@Data
public class UserLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Utf8ByteLength
    private String password;
}
