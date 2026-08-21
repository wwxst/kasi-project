package com.kasi.backend.drama.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("平台分佣规则持久化")
class ProviderCommissionRulePersistenceTest extends BaseAuthTest {

    @Autowired
    private ProviderCommissionRuleMapper mapper;

    @Autowired
    private ShortDramaProviderMapper providerMapper;

    @Test
    @DisplayName("平台规则按时间匹配并保留十位小数费率")
    void ruleCanBeStoredAndResolvedByTime() {
        Long providerId = providerId();
        ProviderCommissionRule first = rule(providerId,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        ProviderCommissionRule second = rule(providerId,
                LocalDateTime.of(2026, 9, 1, 0, 0), null);
        assertThat(mapper.insert(first)).isEqualTo(1);
        assertThat(mapper.insert(second)).isEqualTo(1);

        assertThat(mapper.findEffective(providerId, LocalDateTime.of(2026, 8, 31, 23, 59)).getId())
                .isEqualTo(first.getId());
        assertThat(mapper.findEffective(providerId, LocalDateTime.of(2026, 9, 1, 0, 0)).getId())
                .isEqualTo(second.getId());
        assertThat(mapper.findByIdAndProviderId(first.getId(), providerId).getChannelFeeRate())
                .isEqualByComparingTo("0.3000000000");
    }

    @Test
    @DisplayName("重叠查询允许相邻区间并拒绝交叉区间")
    void overlapQueryUsesHalfOpenIntervals() {
        Long providerId = providerId();
        mapper.insert(rule(providerId, LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0)));

        assertThat(mapper.countOverlapping(providerId, null,
                LocalDateTime.of(2026, 9, 1, 0, 0), null)).isZero();
        assertThat(mapper.countOverlapping(providerId, null,
                LocalDateTime.of(2026, 8, 15, 0, 0), LocalDateTime.of(2026, 9, 15, 0, 0)))
                .isEqualTo(1);
    }

    private Long providerId() {
        return providerMapper.findByCode("GOODSHORT").getId();
    }

    private ProviderCommissionRule rule(Long providerId, LocalDateTime from, LocalDateTime to) {
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setProviderId(providerId);
        rule.setChannelFeeRate(new BigDecimal("0.3000000000"));
        rule.setPrincipalFeeRate(BigDecimal.ZERO);
        rule.setPrincipalCommissionRate(new BigDecimal("0.8000000000"));
        rule.setDownstreamFeeRate(BigDecimal.ZERO);
        rule.setDownstreamCommissionRate(new BigDecimal("0.7000000000"));
        rule.setEffectiveFrom(from);
        rule.setEffectiveTo(to);
        rule.setCreatedBy(1L);
        rule.setUpdatedBy(1L);
        return rule;
    }
}
