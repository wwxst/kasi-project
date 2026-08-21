package com.kasi.backend.drama.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("平台分佣规则并发写入")
class ProviderCommissionRuleConcurrencyTest extends BaseAuthTest {
    @Autowired
    private ProviderCommissionRuleService service;

    @Autowired
    private ShortDramaProviderMapper providerMapper;

    @Autowired
    private ProviderCommissionRuleMapper ruleMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("同一平台并发创建重叠规则最多成功一条")
    void concurrentCreateSerializesOnProviderRow() throws Exception {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        CountDownLatch providerLocked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            providerMapper.findByIdForUpdate(providerId);
            providerLocked.countDown();
            sleep(500);
            service.create(1L, providerId, request(from));
            return null;
        }));
        assertThat(providerLocked.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> service.create(1L, providerId, request(from)));
        first.get(10, TimeUnit.SECONDS);
        assertThatThrownBusinessException(second);
        executor.shutdownNow();

        assertThat(ruleMapper.findAllByProviderId(providerId)).hasSize(1);
    }

    private void assertThatThrownBusinessException(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) exception.getCause()).getCode()).isEqualTo(6013);
            return;
        }
        throw new AssertionError("expected concurrent create to reject overlapping rule");
    }

    private CreateCommissionRuleDTO request(LocalDateTime from) {
        CreateCommissionRuleDTO request = new CreateCommissionRuleDTO();
        request.setChannelFeeRate(new BigDecimal("30"));
        request.setPrincipalFeeRate(BigDecimal.ZERO);
        request.setPrincipalCommissionRate(new BigDecimal("80"));
        request.setDownstreamFeeRate(BigDecimal.ZERO);
        request.setDownstreamCommissionRate(new BigDecimal("70"));
        request.setEffectiveFrom(from);
        return request;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
