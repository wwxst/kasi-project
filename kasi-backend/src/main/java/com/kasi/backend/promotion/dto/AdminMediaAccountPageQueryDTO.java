package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminMediaAccountPageQueryDTO {
    @Min(1) private int page = 1;
    @Min(1) @Max(100) private int size = 20;
    private String userNo;
    private MediaType mediaType;
    private Integer accountStatus;
    private Long providerId;
    private FilingStatus filingStatus;
}
