package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PromotionLinkBatchVO {
    private String batchNo;
    private String requestKey;
    private List<PromotionLinkVO> links;
    private boolean complete;
}
