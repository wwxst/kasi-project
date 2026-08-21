package com.kasi.backend.drama.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.enums.CommissionRuleStatus;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCommissionRuleServiceImpl implements ProviderCommissionRuleService {
    private final ProviderCommissionRuleMapper ruleMapper;
    private final ShortDramaProviderMapper providerMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderCommissionRuleVO> getRules(Long providerId) {
        requireProviderForRead(providerId);
        LocalDateTime now = LocalDateTime.now(clock);
        return ruleMapper.findAllByProviderId(providerId).stream()
                .map(rule -> toVO(rule, now))
                .toList();
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO create(Long operatorId, Long providerId,
                                           CreateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        validateWindow(request.getEffectiveFrom(), request.getEffectiveTo());
        ensureNoOverlap(providerId, null, request.getEffectiveFrom(), request.getEffectiveTo());
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setProviderId(providerId);
        applyRates(rule, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        rule.setEffectiveFrom(request.getEffectiveFrom());
        rule.setEffectiveTo(request.getEffectiveTo());
        rule.setCreatedBy(operatorId);
        rule.setUpdatedBy(operatorId);
        if (ruleMapper.insert(rule) != 1) {
            throw new IllegalStateException("平台分佣规则创建失败");
        }
        return toVO(reloadIfPossible(rule), LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO update(Long operatorId, Long providerId, Long ruleId,
                                           UpdateCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (statusOf(existing, now) != CommissionRuleStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        validateWindow(request.getEffectiveFrom(), request.getEffectiveTo());
        ensureNoOverlap(providerId, ruleId, request.getEffectiveFrom(), request.getEffectiveTo());
        applyRates(existing, request.getChannelFeeRate(), request.getPrincipalFeeRate(),
                request.getPrincipalCommissionRate(), request.getDownstreamFeeRate(),
                request.getDownstreamCommissionRate());
        existing.setEffectiveFrom(request.getEffectiveFrom());
        existing.setEffectiveTo(request.getEffectiveTo());
        existing.setUpdatedBy(operatorId);
        if (ruleMapper.update(existing) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        return toVO(reloadIfPossible(existing), now);
    }

    @Override
    @Transactional
    public ProviderCommissionRuleVO end(Long operatorId, Long providerId, Long ruleId,
                                        EndCommissionRuleDTO request) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime newEnd = request.getEffectiveTo();
        validateSecondPrecision(newEnd);
        if (statusOf(existing, now) != CommissionRuleStatus.ACTIVE
                || !newEnd.isAfter(now)
                || (existing.getEffectiveTo() != null && !newEnd.isBefore(existing.getEffectiveTo()))) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        ensureNoOverlap(providerId, ruleId, existing.getEffectiveFrom(), newEnd);
        if (ruleMapper.updateEffectiveTo(ruleId, providerId, newEnd, operatorId) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        existing.setEffectiveTo(newEnd);
        existing.setUpdatedBy(operatorId);
        return toVO(reloadIfPossible(existing), now);
    }

    @Override
    @Transactional
    public void delete(Long operatorId, Long providerId, Long ruleId) {
        requireProviderForWrite(providerId);
        ProviderCommissionRule existing = requireRule(providerId, ruleId);
        if (statusOf(existing, LocalDateTime.now(clock)) != CommissionRuleStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_STATE_INVALID);
        }
        if (ruleMapper.delete(ruleId, providerId) != 1) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        log.info("管理员删除未来平台分佣规则: operatorId={}, providerId={}, ruleId={}",
                operatorId, providerId, ruleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderCommissionRule> findEffectiveRule(Long providerId, LocalDateTime at) {
        return Optional.ofNullable(ruleMapper.findEffective(providerId, at));
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

    private ProviderCommissionRule requireRule(Long providerId, Long ruleId) {
        ProviderCommissionRule rule = ruleMapper.findByIdAndProviderId(ruleId, providerId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_NOT_FOUND);
        }
        return rule;
    }

    private void validateWindow(LocalDateTime from, LocalDateTime to) {
        validateSecondPrecision(from);
        if (to != null) {
            validateSecondPrecision(to);
        }
        if (to != null && (!from.isBefore(to) || !to.isAfter(LocalDateTime.now(clock)))) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_TIME_INVALID);
        }
    }

    private void validateSecondPrecision(LocalDateTime time) {
        if (time.getNano() != 0) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_TIME_INVALID);
        }
    }

    private void ensureNoOverlap(Long providerId, Long excludeId,
                                 LocalDateTime from, LocalDateTime to) {
        if (ruleMapper.countOverlapping(providerId, excludeId, from, to) > 0) {
            throw new BusinessException(ErrorCode.PROVIDER_COMMISSION_RULE_TIME_OVERLAP);
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

    private CommissionRuleStatus statusOf(ProviderCommissionRule rule, LocalDateTime now) {
        if (rule.getEffectiveFrom().isAfter(now)) {
            return CommissionRuleStatus.PENDING;
        }
        if (rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(now)) {
            return CommissionRuleStatus.ACTIVE;
        }
        return CommissionRuleStatus.ENDED;
    }

    private ProviderCommissionRuleVO toVO(ProviderCommissionRule rule, LocalDateTime now) {
        return ProviderCommissionRuleVO.builder()
                .id(rule.getId())
                .providerId(rule.getProviderId())
                .channelFeeRate(toPercent(rule.getChannelFeeRate()))
                .principalFeeRate(toPercent(rule.getPrincipalFeeRate()))
                .principalCommissionRate(toPercent(rule.getPrincipalCommissionRate()))
                .downstreamFeeRate(toPercent(rule.getDownstreamFeeRate()))
                .downstreamCommissionRate(toPercent(rule.getDownstreamCommissionRate()))
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .status(statusOf(rule, now))
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
