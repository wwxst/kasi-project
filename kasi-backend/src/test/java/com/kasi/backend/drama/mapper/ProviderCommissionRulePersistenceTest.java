package com.kasi.backend.drama.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("平台默认分佣规则持久化")
class ProviderCommissionRulePersistenceTest extends BaseAuthTest {
    @Autowired
    private ProviderCommissionRuleMapper mapper;

    @Autowired
    private ShortDramaProviderMapper providerMapper;

    @Test
    @DisplayName("每个平台只保存一条默认规则并保留高精度费率")
    void defaultRuleCanBeStoredAndRead() {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        ProviderCommissionRule rule = rule(providerId);
        assertThat(mapper.insert(rule)).isEqualTo(1);

        assertThat(mapper.findByProviderId(providerId).getChannelFeeRate())
                .isEqualByComparingTo("0.3000000000");
        assertThat(mapper.findAllByProviderId(providerId)).hasSize(1);
    }

    @Test
    @DisplayName("同一平台重复默认规则被唯一约束拒绝")
    void duplicateProviderRuleIsRejected() {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        mapper.insert(rule(providerId));

        assertThatThrownBy(() -> mapper.insert(rule(providerId)))
                .isInstanceOf(Exception.class);
    }

    private ProviderCommissionRule rule(Long providerId) {
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setProviderId(providerId);
        rule.setChannelFeeRate(new BigDecimal("0.3000000000"));
        rule.setPrincipalFeeRate(BigDecimal.ZERO);
        rule.setPrincipalCommissionRate(new BigDecimal("0.8000000000"));
        rule.setDownstreamFeeRate(BigDecimal.ZERO);
        rule.setDownstreamCommissionRate(new BigDecimal("0.7000000000"));
        rule.setCreatedBy(1L);
        rule.setUpdatedBy(1L);
        return rule;
    }
}
