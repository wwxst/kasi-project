package com.kasi.backend.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSmsConfigDTO {

    @Size(max = 128, message = "AccessKey ID长度不能超过128位")
    private String accessKeyId;

    @Size(max = 256, message = "AccessKey Secret长度不能超过256位")
    private String accessKeySecret;

    @NotBlank(message = "短信签名不能为空")
    @Size(max = 64, message = "短信签名长度不能超过64位")
    private String signName;

    @NotBlank(message = "注册模板Code不能为空")
    @Pattern(regexp = "SMS_[0-9]+", message = "注册模板Code格式不正确")
    private String registerTemplateCode;

    @NotBlank(message = "登录模板Code不能为空")
    @Pattern(regexp = "SMS_[0-9]+", message = "登录模板Code格式不正确")
    private String loginTemplateCode;

    @NotBlank(message = "忘记密码模板Code不能为空")
    @Pattern(regexp = "SMS_[0-9]+", message = "忘记密码模板Code格式不正确")
    private String resetPasswordTemplateCode;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
    @Size(max = 255) private String smtpHost;
    private Integer smtpPort;
    @Size(max = 255) private String smtpUsername;
    @Size(max = 512) private String smtpPassword;
    @Size(max = 255) private String smtpFromAddress;
    @NotNull private Boolean emailEnabled;
}
