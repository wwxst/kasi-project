package com.kasi.backend.admin.dto;

import com.kasi.backend.common.validation.OptionalEmail;
import com.kasi.backend.common.validation.OptionalMobile;
import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdminDTO {
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号不能超过64位")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "登录账号只能包含英文字母和数字")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须为8到72位")
    @Pattern(regexp = "^[!-~]+$", message = "密码只能包含ASCII字母、数字和特殊符号")
    @Utf8ByteLength
    private String password;
    @NotBlank(message = "确认密码不能为空")
    @Size(min = 8, max = 72, message = "确认密码长度必须为8到72位")
    @Pattern(regexp = "^[!-~]+$", message = "确认密码只能包含ASCII字母、数字和特殊符号")
    @Utf8ByteLength
    private String confirmPassword;
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名不能超过64位")
    @Pattern(regexp = "^\\S+$", message = "真实姓名不能包含空白字符")
    private String realName;
    @OptionalMobile
    private String mobile;
    @OptionalEmail
    private String email;
    @Size(max = 512, message = "头像地址不能超过512位")
    private String avatarUrl;
    private Long departmentId;
    @Size(max = 500, message = "备注不能超过500位")
    private String remark;
}
