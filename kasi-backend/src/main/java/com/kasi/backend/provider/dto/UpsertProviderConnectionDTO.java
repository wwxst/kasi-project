package com.kasi.backend.provider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.kasi.backend.provider.enums.FilingMode;
import lombok.Data;
import lombok.ToString;

@Data
public class UpsertProviderConnectionDTO {

    @Size(max = 253)
    @Pattern(regexp = "(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$",
            message = "媒体根域必须是有效 hostname，不包含协议、端口、路径或通配符")
    private String mediaRootDomain;

    @Size(max = 512)
    @Pattern(regexp = "^https?://[^\\s]+$", message = "接口域名必须是有效的 HTTP 或 HTTPS 地址")
    private String baseUrl;

    @Size(max = 64)
    private String connectionName;

    @Size(max = 64)
    private String partnerId;

    @Size(max = 256)
    @ToString.Exclude
    private String apiKey;

    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;

    private FilingMode filingMode;

    @AssertTrue(message = "API 报备模式下接口 URL、媒体根域名和 PID 不能为空")
    public boolean isApiConfigurationPresent() {
        if (filingMode == FilingMode.MANUAL) {
            return true;
        }
        return baseUrl != null && !baseUrl.isBlank()
                && mediaRootDomain != null && !mediaRootDomain.isBlank()
                && partnerId != null && !partnerId.isBlank();
    }
}
