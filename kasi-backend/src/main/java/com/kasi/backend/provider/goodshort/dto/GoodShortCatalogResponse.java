package com.kasi.backend.provider.goodshort.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class GoodShortCatalogResponse {
    private Integer status;
    private Boolean success;
    private String message;
    private GoodShortCatalogData data;

    @Data
    public static class GoodShortCatalogData {
        @JsonAlias({"items", "list", "books", "records"})
        private List<GoodShortBookData> items;
        private Integer pageNo;
        private Integer pageSize;
        private Long total;
        private Boolean hasNext;
        private Long nextUpdateTime;
        private Long updateTime;
    }
}
