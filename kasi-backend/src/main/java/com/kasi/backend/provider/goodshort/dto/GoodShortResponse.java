package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

@Data
public class GoodShortResponse {

    private Integer status;
    private Boolean success;
    private String message;
    private GoodShortFilingData data;
}
