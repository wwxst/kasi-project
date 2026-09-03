package com.kasi.backend.provider.vo;

import com.kasi.backend.provider.enums.FilingMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderFilingModeVO {
    private Long providerId;
    private String providerName;
    private FilingMode filingMode;
}
