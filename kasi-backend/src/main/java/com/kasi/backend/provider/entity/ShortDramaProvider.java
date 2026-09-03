package com.kasi.backend.provider.entity;

import lombok.Data;

@Data
public class ShortDramaProvider {
    private Long id;
    private String providerCode;
    private String providerName;
    private Integer status;
}
