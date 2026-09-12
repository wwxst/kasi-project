package com.kasi.backend.provider.vo;

import com.kasi.backend.promotion.enums.MediaType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProviderConnectionVO {

    private Long id;
    private String connectionName;
    private String mediaRootDomain;
    private String baseUrl;
    private String partnerId;
    private String currency;
    private Integer status;
    private boolean credentialConfigured;
    private List<MediaType> apiFilingMediaTypes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
