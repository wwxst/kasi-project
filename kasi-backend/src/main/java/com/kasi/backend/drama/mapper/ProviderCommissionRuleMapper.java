package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderCommissionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderCommissionRuleMapper {
    int insert(ProviderCommissionRule rule);

    List<ProviderCommissionRule> findAllByProviderId(@Param("providerId") Long providerId);

    ProviderCommissionRule findByIdAndProviderId(@Param("id") Long id,
                                                   @Param("providerId") Long providerId);

    ProviderCommissionRule findEffective(@Param("providerId") Long providerId,
                                          @Param("at") LocalDateTime at);

    long countOverlapping(@Param("providerId") Long providerId,
                          @Param("excludeId") Long excludeId,
                          @Param("effectiveFrom") LocalDateTime effectiveFrom,
                          @Param("effectiveTo") LocalDateTime effectiveTo);

    int update(ProviderCommissionRule rule);

    int updateEffectiveTo(@Param("id") Long id,
                          @Param("providerId") Long providerId,
                          @Param("effectiveTo") LocalDateTime effectiveTo,
                          @Param("updatedBy") Long updatedBy);

    int delete(@Param("id") Long id, @Param("providerId") Long providerId);
}
