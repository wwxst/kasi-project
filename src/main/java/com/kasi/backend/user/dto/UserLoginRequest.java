package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求
 */
@Data
public class UserLoginRequest {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
