package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderCommissionRuleHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderCommissionRuleHistoryMapper {
    int insert(ProviderCommissionRuleHistory history);

    ProviderCommissionRuleHistory findLatestByProviderId(@Param("providerId") Long providerId);

    List<ProviderCommissionRuleHistory> findAllByProviderId(@Param("providerId") Long providerId);
}
