package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderCommissionRuleServiceImpl implements ProviderCommissionRuleService {
    private final ProviderCommissionRuleMapper ruleMapper;
    private final ShortDramaProviderMapper providerMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderCommissionRuleVO> getRules(Long providerId) {
        requireProviderForRead(providerId);
        ProviderCommissionRule rule = ruleMapper.findByProviderId(providerId);
        return rule == null ? List.of() : List.of(toVO(rule));
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO create(Long operatorId, Long providerId,
                                           CreateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        if (ruleMapper.findByProviderId(providerId) != null) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_EXISTS);
        }
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setProviderId(providerId);
        applyRates(rule, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        rule.setCreatedBy(operatorId);
        rule.setUpdatedBy(operatorId);
        if (ruleMapper.insert(rule) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_EXISTS);
        }
        return toVO(reloadIfPossible(rule));
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                           UpdateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = ruleMapper.findByIdAndProviderId(ruleId, providerId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        applyRates(existing, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        existing.setUpdatedBy(operatorId);
        if (ruleMapper.update(existing) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        return toVO(reloadIfPossible(existing));
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderCommissionRule findDefaultRule(Long providerId) {
        requireProviderForRead(providerId);
        return ruleMapper.findByProviderId(providerId);
    }

    private void requireProviderForRead(Long providerId) {
        if (providerMapper.findById(providerId) == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
    }

    private void requireProviderForWrite(Long providerId) {
        if (providerMapper.findByIdForUpdate(providerId) == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
    }

    private void applyRates(ProviderCommissionRule rule, BigDecimal channel,
                            BigDecimal principalFee, BigDecimal principalCommission,
                            BigDecimal downstreamFee, BigDecimal downstreamCommission) {
        rule.setChannelFeeRate(toRatio(channel));
        rule.setPrincipalFeeRate(toRatio(principalFee));
        rule.setPrincipalCommissionRate(toRatio(principalCommission));
        rule.setDownstreamFeeRate(toRatio(downstreamFee));
        rule.setDownstreamCommissionRate(toRatio(downstreamCommission));
    }

    private ProviderCommissionRule reloadIfPossible(ProviderCommissionRule rule) {
        if (rule.getId() == null) {
            return rule;
        }
        ProviderCommissionRule stored = ruleMapper.findByIdAndProviderId(rule.getId(), rule.getProviderId());
        return stored == null ? rule : stored;
    }

    private BigDecimal toRatio(BigDecimal percent) {
        return percent.movePointLeft(2);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return ratio.movePointRight(2).stripTrailingZeros();
    }

    private ProviderCommissionRuleVO toVO(ProviderCommissionRule rule) {
        return ProviderCommissionRuleVO.builder()
                .id(rule.getId())
                .providerId(rule.getProviderId())
                .channelFeeRate(toPercent(rule.getChannelFeeRate()))
                .principalFeeRate(toPercent(rule.getPrincipalFeeRate()))
                .principalCommissionRate(toPercent(rule.getPrincipalCommissionRate()))
                .downstreamFeeRate(toPercent(rule.getDownstreamFeeRate()))
                .downstreamCommissionRate(toPercent(rule.getDownstreamCommissionRate()))
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
