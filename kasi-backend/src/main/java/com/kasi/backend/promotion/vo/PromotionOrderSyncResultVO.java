package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromotionOrderSyncResultVO {
    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int unattributedCount;
}
