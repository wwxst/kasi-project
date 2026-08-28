package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

import java.util.List;

@Data
public class GoodShortFreeContentResponse {
    private Integer status;
    private Boolean success;
    private String message;
    private List<GoodShortFreeContentData> data;

    @Data
    public static class GoodShortFreeContentData {
        private String chapterName;
        private String content;
    }
}
