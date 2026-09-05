package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminMediaAccountListItemVO {
    private Long id;
    private String userNo;
    private String nickname;
    private String realName;
    private MediaType mediaType;
    private String externalAccountId;
    private String accountName;
    private Long providerId;
    private Integer status;
    private FilingStatus filingStatus;
    private LocalDateTime filingLastSubmittedAt;
    private LocalDateTime filingNextActionAt;
    private LocalDateTime updatedAt;
}
