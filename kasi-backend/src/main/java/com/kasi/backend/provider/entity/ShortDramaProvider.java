package com.kasi.backend.provider.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortDramaProvider {
    private Long id;
    private String providerCode;
    private String providerName;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
