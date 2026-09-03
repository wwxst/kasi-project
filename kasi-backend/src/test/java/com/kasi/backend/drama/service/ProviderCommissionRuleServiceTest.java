package com.kasi.backend.drama.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleHistoryMapper;
import com.kasi.backend.drama.service.impl.ProviderCommissionRuleServiceImpl;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("平台默认分佣规则服务")
class ProviderCommissionRuleServiceTest {
    private ProviderCommissionRuleMapper ruleMapper;
    private ShortDramaProviderMapper providerMapper;
    private ProviderCommissionRuleHistoryMapper historyMapper;
    private ProviderCommissionRuleService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(ProviderCommissionRuleMapper.class);
        providerMapper = mock(ShortDramaProviderMapper.class);
        historyMapper = mock(ProviderCommissionRuleHistoryMapper.class);
        service = new ProviderCommissionRuleServiceImpl(ruleMapper, providerMapper, historyMapper);
        when(providerMapper.findById(7L)).thenReturn(provider(7L));
        when(providerMapper.findByIdForUpdate(7L)).thenReturn(provider(7L));
        when(ruleMapper.insert(any(ProviderCommissionRule.class))).thenReturn(1);
        when(ruleMapper.update(any(ProviderCommissionRule.class))).thenReturn(1);
    }

    @Test
    @DisplayName("首次设置将百分比转换为比例并返回默认规则")
    void createDefaultRuleConvertsPercent() {
        ProviderCommissionRuleVO result = service.create(1L, 7L, request());

        ArgumentCaptor<ProviderCommissionRule> captor = ArgumentCaptor.forClass(ProviderCommissionRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertThat(captor.getValue().getChannelFeeRate()).isEqualByComparingTo("0.3000");
        assertThat(result.getChannelFeeRate()).isEqualByComparingTo("30");
        assertThat(result.getId()).isNull();
    }

    @Test
    @DisplayName("已有默认规则时禁止重复创建")
    void createRejectsExistingDefaultRule() {
        when(ruleMapper.findByProviderId(7L)).thenReturn(storedRule(11L));

        assertThatThrownBy(() -> service.create(1L, 7L, request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6012));
        verify(ruleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("编辑默认规则直接覆盖五项费率")
    void updateOverwritesDefaultRule() {
        ProviderCommissionRule existing = storedRule(11L);
        when(ruleMapper.findByIdAndProviderId(11L, 7L)).thenReturn(existing);

        UpdateCommissionRuleDTO update = new UpdateCommissionRuleDTO();
        update.setChannelFeeRate(new BigDecimal("40"));
        update.setPrincipalFeeRate(BigDecimal.ZERO);
        update.setPrincipalCommissionRate(new BigDecimal("75"));
        update.setDownstreamFeeRate(BigDecimal.ZERO);
        update.setDownstreamCommissionRate(new BigDecimal("65"));

        ProviderCommissionRuleVO result = service.update(1L, 7L, 11L, update);

        assertThat(existing.getChannelFeeRate()).isEqualByComparingTo("0.40");
        assertThat(result.getPrincipalCommissionRate()).isEqualByComparingTo("75");
        verify(ruleMapper).update(existing);
    }

    @Test
    @DisplayName("编辑不存在的默认规则返回不存在")
    void updateMissingRuleReturnsNotFound() {
        assertThatThrownBy(() -> service.update(1L, 7L, 99L, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6011));
    }

    private CreateCommissionRuleDTO request() {
        CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal("30"));
        request.setPrincipalFeeRate(BigDecimal.ZERO);
        request.setPrincipalCommissionRate(new BigDecimal("80"));
        request.setDownstreamFeeRate(BigDecimal.ZERO);
        request.setDownstreamCommissionRate(new BigDecimal("70"));
        return request;
    }

    private UpdateCommissionRuleDTO updateRequest() {
        UpdateCommissionRuleDTO request = new UpdateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal("30"));
        request.setPrincipalFeeRate(BigDecimal.ZERO);
        request.setPrincipalCommissionRate(new BigDecimal("80"));
        request.setDownstreamFeeRate(BigDecimal.ZERO);
        request.setDownstreamCommissionRate(new BigDecimal("70"));
        return request;
    }

    private ProviderCommissionRule storedRule(Long id) {
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setId(id);
        rule.setProviderId(7L);
        rule.setChannelFeeRate(new BigDecimal("0.30"));
        rule.setPrincipalFeeRate(BigDecimal.ZERO);
        rule.setPrincipalCommissionRate(new BigDecimal("0.80"));
        rule.setDownstreamFeeRate(BigDecimal.ZERO);
        rule.setDownstreamCommissionRate(new BigDecimal("0.70"));
        return rule;
    }

    private ShortDramaProvider provider(Long id) {
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(id);
        provider.setProviderCode("GOODSHORT");
        return provider;
    }
}
