package com.kasi.backend.admin.dto;

import com.kasi.backend.common.validation.OptionalEmail;
import com.kasi.backend.common.validation.OptionalMobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAdminDTO {
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号不能超过64位")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "登录账号只能包含英文字母和数字")
    private String username;
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
