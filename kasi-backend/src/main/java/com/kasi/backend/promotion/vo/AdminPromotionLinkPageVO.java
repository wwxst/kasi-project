package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminPromotionLinkPageVO {
    private List<AdminPromotionLinkVO> list;
    private int page;
    private int size;
    private long total;
}
