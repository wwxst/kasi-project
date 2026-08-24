package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.entity.PromotionOrderMonthlySummary;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromotionOrderMapper {
    int insert(PromotionOrder order);

    PromotionOrder findBySource(@Param("connectionId") Long connectionId,
                                @Param("externalOrderId") String externalOrderId);

    PromotionOrder findBySourceForUpdate(@Param("connectionId") Long connectionId,
                                         @Param("externalOrderId") String externalOrderId);

    int updateSourceFields(PromotionOrder order);

    int markCommissionReversed(@Param("id") Long id);

    int applyAttributionAndCommission(PromotionOrder order);

    long countPage(@Param("providerId") Long providerId,
                   @Param("userId") Long userId,
                   @Param("status") PromotionOrderStatus status,
                   @Param("attributionStatus") PromotionAttributionStatus attributionStatus,
                   @Param("startDate") java.time.LocalDateTime startDate,
                   @Param("endDate") java.time.LocalDateTime endDate);

    java.util.List<PromotionOrder> findPage(
            @Param("providerId") Long providerId,
            @Param("userId") Long userId,
            @Param("status") PromotionOrderStatus status,
            @Param("attributionStatus") PromotionAttributionStatus attributionStatus,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("offset") int offset,
            @Param("size") int size);

    PromotionOrderMonthlySummary summarizeMonth(
            @Param("userId") Long userId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate);
}
