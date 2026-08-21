package com.kasi.backend.drama.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.EndCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.enums.CommissionRuleStatus;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.drama.service.impl.ProviderCommissionRuleServiceImpl;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("平台分佣规则服务")
class ProviderCommissionRuleServiceTest {
    private ProviderCommissionRuleMapper ruleMapper;
    private ShortDramaProviderMapper providerMapper;
    private ProviderCommissionRuleService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(ProviderCommissionRuleMapper.class);
        providerMapper = mock(ShortDramaProviderMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T08:00:00Z"), ZoneOffset.UTC);
        service = new ProviderCommissionRuleServiceImpl(ruleMapper, providerMapper, clock);
        when(providerMapper.findById(7L)).thenReturn(provider(7L));
        when(ruleMapper.insert(any(ProviderCommissionRule.class))).thenReturn(1);
        when(ruleMapper.update(any(ProviderCommissionRule.class))).thenReturn(1);
        when(ruleMapper.updateEffectiveTo(anyLong(), anyLong(), any(), anyLong())).thenReturn(1);
        when(ruleMapper.delete(anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("新增规则把百分比转换为比例并返回生效状态")
    void createConvertsPercentAndReturnsStatus() {
        when(ruleMapper.countOverlapping(eq(7L), isNull(), any(), isNull())).thenReturn(0L);
        CreateCommissionRuleDTO request = createRequest(
                LocalDateTime.of(2026, 8, 1, 0, 0), null);

        ProviderCommissionRuleVO result = service.create(1L, 7L, request);

        ArgumentCaptor<ProviderCommissionRule> captor = ArgumentCaptor.forClass(ProviderCommissionRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertThat(captor.getValue().getChannelFeeRate()).isEqualByComparingTo("0.3000");
        assertThat(result.getChannelFeeRate()).isEqualByComparingTo("30");
        assertThat(result.getStatus()).isEqualTo(CommissionRuleStatus.ACTIVE);
    }

    @Test
    @DisplayName("重叠规则和非法时间被拒绝")
    void createRejectsOverlapAndInvalidTime() {
        when(ruleMapper.countOverlapping(anyLong(), isNull(), any(), any())).thenReturn(1L);
        assertBusinessCode(() -> service.create(1L, 7L, createRequest(
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0))), 6013);
        assertBusinessCode(() -> service.create(1L, 7L, createRequest(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0))), 6012);
    }

    @Test
    @DisplayName("新增规则不能事后补录已经结束的历史区间")
    void createRejectsAlreadyEndedWindow() {
        assertBusinessCode(() -> service.create(1L, 7L, createRequest(
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0))), 6012);

        verify(ruleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("规则时间必须与数据库保持相同的整秒精度")
    void writesRejectFractionalSeconds() {
        assertBusinessCode(() -> service.create(1L, 7L, createRequest(
                LocalDateTime.of(2026, 9, 1, 0, 0, 0, 1), null)), 6012);

        ProviderCommissionRule future = storedRule(11L,
                LocalDateTime.of(2026, 9, 1, 0, 0), null);
        when(ruleMapper.findByIdAndProviderId(11L, 7L)).thenReturn(future);
        assertBusinessCode(() -> service.update(1L, 7L, 11L, updateRequest(
                LocalDateTime.of(2026, 9, 2, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0, 0, 1))), 6012);

        ProviderCommissionRule active = storedRule(21L,
                LocalDateTime.of(2026, 8, 1, 0, 0), null);
        when(ruleMapper.findByIdAndProviderId(21L, 7L)).thenReturn(active);
        EndCommissionRuleDTO end = new EndCommissionRuleDTO();
        end.setEffectiveTo(LocalDateTime.of(2026, 8, 22, 0, 0, 0, 1));
        assertBusinessCode(() -> service.end(1L, 7L, 21L, end), 6012);
    }

    @Test
    @DisplayName("未来规则可以修改和删除")
    void futureRuleCanBeUpdatedAndDeleted() {
        ProviderCommissionRule futureForUpdate = storedRule(11L,
                LocalDateTime.of(2026, 9, 1, 0, 0), null);
        ProviderCommissionRule futureForDelete = storedRule(12L,
                LocalDateTime.of(2026, 10, 1, 0, 0), null);
        when(ruleMapper.findByIdAndProviderId(11L, 7L)).thenReturn(futureForUpdate);
        when(ruleMapper.findByIdAndProviderId(12L, 7L)).thenReturn(futureForDelete);
        when(ruleMapper.countOverlapping(eq(7L), eq(11L), any(), any())).thenReturn(0L);

        UpdateCommissionRuleDTO update = updateRequest(
                LocalDateTime.of(2026, 9, 2, 0, 0), null);
        assertThat(service.update(1L, 7L, 11L, update).getStatus())
                .isEqualTo(CommissionRuleStatus.PENDING);
        service.delete(1L, 7L, 12L);

        verify(ruleMapper).update(futureForUpdate);
        verify(ruleMapper).delete(12L, 7L);
    }

    @Test
    @DisplayName("当前规则只能提前结束而历史规则完全只读")
    void activeCanOnlyEndAndEndedIsReadOnly() {
        ProviderCommissionRule active = storedRule(21L,
                LocalDateTime.of(2026, 8, 1, 0, 0), null);
        ProviderCommissionRule ended = storedRule(31L,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
        when(ruleMapper.findByIdAndProviderId(21L, 7L)).thenReturn(active);
        when(ruleMapper.findByIdAndProviderId(31L, 7L)).thenReturn(ended);
        when(ruleMapper.countOverlapping(eq(7L), eq(21L), any(), any())).thenReturn(0L);
        EndCommissionRuleDTO end = new EndCommissionRuleDTO();
        end.setEffectiveTo(LocalDateTime.of(2026, 8, 22, 0, 0));

        assertThat(service.end(1L, 7L, 21L, end).getEffectiveTo())
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 0, 0));
        assertBusinessCode(() -> service.update(1L, 7L, 21L,
                updateRequest(LocalDateTime.of(2026, 9, 1, 0, 0), null)), 6014);
        assertBusinessCode(() -> service.delete(1L, 7L, 21L), 6014);
        assertBusinessCode(() -> service.delete(1L, 7L, 31L), 6014);
    }

    private ShortDramaProvider provider(Long id) {
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(id);
        provider.setProviderCode("GOODSHORT");
        return provider;
    }

    private ProviderCommissionRule storedRule(Long id, LocalDateTime from, LocalDateTime to) {
        ProviderCommissionRule rule = new ProviderCommissionRule();
        rule.setId(id);
        rule.setProviderId(7L);
        rule.setChannelFeeRate(new BigDecimal("0.30"));
        rule.setPrincipalFeeRate(BigDecimal.ZERO);
        rule.setPrincipalCommissionRate(new BigDecimal("0.80"));
        rule.setDownstreamFeeRate(BigDecimal.ZERO);
        rule.setDownstreamCommissionRate(new BigDecimal("0.70"));
        rule.setEffectiveFrom(from);
        rule.setEffectiveTo(to);
        return rule;
    }

    private CreateCommissionRuleDTO createRequest(LocalDateTime from, LocalDateTime to) {
        CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal("30"));
        request.setPrincipalFeeRate(BigDecimal.ZERO);
        request.setPrincipalCommissionRate(new BigDecimal("80"));
        request.setDownstreamFeeRate(BigDecimal.ZERO);
        request.setDownstreamCommissionRate(new BigDecimal("70"));
        request.setEffectiveFrom(from);
        request.setEffectiveTo(to);
        return request;
    }

    private UpdateCommissionRuleDTO updateRequest(LocalDateTime from, LocalDateTime to) {
        UpdateCommissionRuleDTO request = new UpdateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal("30"));
        request.setPrincipalFeeRate(BigDecimal.ZERO);
        request.setPrincipalCommissionRate(new BigDecimal("80"));
        request.setDownstreamFeeRate(BigDecimal.ZERO);
        request.setDownstreamCommissionRate(new BigDecimal("70"));
        request.setEffectiveFrom(from);
        request.setEffectiveTo(to);
        return request;
    }

    private void assertBusinessCode(ThrowingCallable call, int code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
