package com.kasi.backend.sms.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SmsConfigVO {

    private boolean configured;
    private boolean accessKeyIdConfigured;
    private boolean accessKeySecretConfigured;
    private String signName;
    private String registerTemplateCode;
    private String loginTemplateCode;
    private String resetPasswordTemplateCode;
    private boolean enabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private boolean smtpPasswordConfigured;
    private String smtpFromAddress;
    private boolean emailEnabled;
    private LocalDateTime updatedAt;
}
