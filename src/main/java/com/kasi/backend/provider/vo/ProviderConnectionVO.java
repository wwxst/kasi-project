package com.kasi.backend.provider.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProviderConnectionVO {

    private Long id;
    private String connectionName;
    private String partnerId;
    private String currency;
    private Integer status;
    private boolean credentialConfigured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
