package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderCommissionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderCommissionRuleMapper {
    int insert(ProviderCommissionRule rule);

    List<ProviderCommissionRule> findAllByProviderId(@Param("providerId") Long providerId);

    ProviderCommissionRule findByIdAndProviderId(@Param("id") Long id,
                                                   @Param("providerId") Long providerId);

    ProviderCommissionRule findByProviderId(@Param("providerId") Long providerId);

    int update(ProviderCommissionRule rule);

}
