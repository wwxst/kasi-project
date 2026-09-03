package com.kasi.backend.sms.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SmsConfig {

    private Long id;
    private String accessKeyIdCiphertext;
    private String accessKeySecretCiphertext;
    private String signName;
    private String registerTemplateCode;
    private String loginTemplateCode;
    private String resetPasswordTemplateCode;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPasswordCiphertext;
    private String smtpFromAddress;
    private Integer emailEnabled;
    private Integer enabled;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
