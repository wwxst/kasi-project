package com.kasi.backend.provider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertProviderConnectionDTO {

    @NotBlank
    @Size(max = 64)
    private String connectionName;

    @NotBlank
    @Size(max = 64)
    private String partnerId;

    @Size(max = 256)
    private String apiKey;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
