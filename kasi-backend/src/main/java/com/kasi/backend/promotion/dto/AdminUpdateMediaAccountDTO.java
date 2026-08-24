package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.MediaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateMediaAccountDTO {
    @NotNull
    private MediaType mediaType;

    @NotBlank
    @Size(max = 128)
    private String externalAccountId;

    @Size(max = 128)
    private String accountName;

    @Pattern(regexp = "^https://.+", message = "主页链接必须使用HTTPS")
    @Size(max = 512)
    private String accountLink;

    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
