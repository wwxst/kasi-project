package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaAccountFilingExportRow {
    private LocalDateTime createdAt;
    private String nickname;
    private String realName;
    private String mobile;
    private String wechatId;
    private MediaType mediaType;
    private String externalAccountId;
    private String accountName;
    private String accountLink;
    private FilingStatus filingStatus;
    private String providerName;
}
