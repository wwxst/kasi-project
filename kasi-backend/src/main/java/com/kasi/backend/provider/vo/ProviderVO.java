package com.kasi.backend.provider.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderVO {

    private Long id;
    private String providerCode;
    private String providerName;
    private Integer status;
    private ProviderConnectionVO connection;
}
