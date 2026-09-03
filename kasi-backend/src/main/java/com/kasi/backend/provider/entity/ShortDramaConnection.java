package com.kasi.backend.provider.entity;

import lombok.Data;
import lombok.ToString;
import com.kasi.backend.provider.enums.FilingMode;

import java.time.LocalDateTime;

@Data
public class ShortDramaConnection {
    private Long id;
    private Long providerId;
    private String connectionName;
    private String baseUrl;
    private String mediaRootDomain;
    private String partnerId;
    @ToString.Exclude
    private String apiKeyCiphertext;
    private String currency;
    private Integer status;
    private FilingMode filingMode;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
