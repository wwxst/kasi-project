package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PromotionProjectPageVO {
    private List<PromotionProjectVO> list;
    private int page;
    private int size;
    private long total;
}
