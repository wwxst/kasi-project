package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;

import java.util.List;

public interface ProviderCommissionRuleService {
    List<ProviderCommissionRuleVO> getRules(Long providerId);

    ProviderCommissionRuleVO create(Long operatorId, Long providerId, CreateCommissionRuleDTO request);

    ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                    UpdateCommissionRuleDTO request);

    ProviderCommissionRule findDefaultRule(Long providerId);
}
