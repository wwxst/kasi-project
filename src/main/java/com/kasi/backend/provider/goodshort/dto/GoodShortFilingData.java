package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

@Data
public class GoodShortFilingData {
    private Integer status;
    private String filingTime;
    private String operateTime;
    private String externalFilingId;
    private String filingId;
}
