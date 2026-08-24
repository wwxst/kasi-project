package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GoodShortOrderResponse {
    private Integer status;
    private Boolean success;
    private String message;
    private GoodShortOrderPageData data;

    @Data
    public static class GoodShortOrderPageData {
        private List<Map<String, Object>> records;
        private Integer pageNo;
        private Integer pageSize;
        private Integer pages;
        private Long total;
    }
}
