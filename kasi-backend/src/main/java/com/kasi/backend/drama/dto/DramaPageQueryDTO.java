package com.kasi.backend.drama.dto;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DramaPageQueryDTO {
    @Min(1)
    private int page = 1;
    @Min(1)
    @Max(100)
    private int size = 20;
    @Positive
    private Long providerId;
    @Size(max = 255)
    private String title;
    @Size(max = 32)
    private String language;
    @Size(max = 32)
    private String remoteShowStatus;
    private DramaLocalStatus localStatus;
}
