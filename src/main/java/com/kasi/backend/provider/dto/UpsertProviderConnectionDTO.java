package com.kasi.backend.provider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class UpsertProviderConnectionDTO {

    @NotBlank
    @Size(max = 512)
    @Pattern(regexp = "^https?://[^\\s]+$", message = "接口域名必须是有效的 HTTP 或 HTTPS 地址")
    private String baseUrl;

    @Size(max = 64)
    private String connectionName;

    @NotBlank
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
}
