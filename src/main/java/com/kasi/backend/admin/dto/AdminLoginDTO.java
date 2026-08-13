package com.kasi.backend.admin.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员登录请求
 */
@Data
public class AdminLoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码不能超过72位")
    @Pattern(regexp = "^[!-~]+$", message = "密码只能包含ASCII字母、数字和特殊符号")
    @Utf8ByteLength
    private String password;
}
