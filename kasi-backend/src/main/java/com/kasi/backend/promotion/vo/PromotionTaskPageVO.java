package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class PromotionTaskPageVO {
    private List<PromotionTaskVO> list; private int page; private int size; private long total;
}
