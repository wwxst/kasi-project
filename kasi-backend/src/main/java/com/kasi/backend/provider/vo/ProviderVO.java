package com.kasi.backend.provider.vo;

import com.kasi.backend.provider.enums.ProviderCapability;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class ProviderVO {

    private Long id;
    private String providerCode;
    private String providerName;
    private Integer status;
    private Set<ProviderCapability> capabilities;
    private ProviderConnectionVO connection;
}
