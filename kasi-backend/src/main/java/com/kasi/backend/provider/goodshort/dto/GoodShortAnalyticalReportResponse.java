package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GoodShortAnalyticalReportResponse {
    private Integer status;
    private Boolean success;
    private String message;
    private GoodShortAnalyticalReportPageData data;

    @Data
    public static class GoodShortAnalyticalReportPageData {
        private List<Map<String, Object>> records;
        private Integer pageNo;
        private Integer pageSize;
        private Integer pages;
        private Long total;
    }
}
