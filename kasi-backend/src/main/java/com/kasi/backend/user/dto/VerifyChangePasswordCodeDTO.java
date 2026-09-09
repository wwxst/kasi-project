package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyChangePasswordCodeDTO {

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为6位数字")
    private String code;
}
