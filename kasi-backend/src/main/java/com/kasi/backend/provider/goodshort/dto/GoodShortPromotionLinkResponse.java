package com.kasi.backend.provider.goodshort.dto;

import lombok.Data;

@Data
public class GoodShortPromotionLinkResponse {
    private Integer status;
    private Boolean success;
    private String message;
    private GoodShortPromotionLinkData data;

    @Data
    public static class GoodShortPromotionLinkData {
        private String code;
        private String customParams;
        private String shareUrl;
    }
}
