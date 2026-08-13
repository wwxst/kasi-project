package com.kasi.backend.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDTO {

    @Pattern(regexp = "^\\s*$|^\\s*1[3-9]\\d{9}\\s*$", message = "手机号格式不正确")
    private String mobile;

    @Pattern(regexp = "^\\s*$|^\\s*[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+\\s*$", message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128位")
    private String email;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过64位")
    private String nickname;

    @Size(max = 64, message = "真实姓名长度不能超过64位")
    private String realName;

    @Size(max = 512, message = "头像地址长度不能超过512位")
    private String avatarUrl;

    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;

    @AssertTrue(message = "手机号或邮箱不能同时为空")
    public boolean isContactProvided() {
        return hasText(mobile) || hasText(email);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
