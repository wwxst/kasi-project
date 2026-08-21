package com.kasi.backend.drama.service;

import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProviderCommissionRuleService {
    List<ProviderCommissionRuleVO> getRules(Long providerId);

    ProviderCommissionRuleVO create(Long operatorId, Long providerId, CreateCommissionRuleDTO request);

    ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                    UpdateCommissionRuleDTO request);

    ProviderCommissionRuleVO end(Long operatorId, Long providerId, Long ruleId,
                                 EndCommissionRuleDTO request);

    void delete(Long operatorId, Long providerId, Long ruleId);

    Optional<ProviderCommissionRule> findEffectiveRule(Long providerId, LocalDateTime at);
}
