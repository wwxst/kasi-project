package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionAnalyticalReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PromotionAnalyticalReportMapper {
    int upsert(PromotionAnalyticalReport report);
    long countPage(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                   @Param("customParams") String customParams, @Param("bookId") String bookId,
                   @Param("code") String code);
    List<PromotionAnalyticalReport> findPage(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                                             @Param("customParams") String customParams, @Param("bookId") String bookId,
                                             @Param("code") String code, @Param("offset") int offset,
                                             @Param("size") int size);
}
