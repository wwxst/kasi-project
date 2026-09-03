package com.kasi.backend.provider.goodshort.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoodShortConnectionProbeRequest {

    private int pageNo;
    private int pageSize;
    private String language;
    private String pid;
    private long timestamp;
}
