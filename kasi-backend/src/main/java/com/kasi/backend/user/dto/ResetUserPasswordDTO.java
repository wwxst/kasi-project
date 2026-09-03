package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetUserPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须为8到72位")
    @Pattern(regexp = "^[!-~]+$", message = "密码只能包含字母、数字和特殊符号")
    @Utf8ByteLength
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Utf8ByteLength
    private String confirmPassword;
}
