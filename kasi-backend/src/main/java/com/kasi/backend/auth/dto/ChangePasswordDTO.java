package com.kasi.backend.auth.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（管理员和用户共用）
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Utf8ByteLength
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "新密码长度不能少于8位")
    @Utf8ByteLength
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Utf8ByteLength
    private String confirmPassword;
}
