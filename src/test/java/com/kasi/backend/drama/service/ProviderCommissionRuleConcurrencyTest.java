package com.kasi.backend.drama.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.mapper.ProviderCommissionRuleMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@DisplayName("平台分佣规则并发写入")
class ProviderCommissionRuleConcurrencyTest extends BaseAuthTest {
    @Autowired
    private ProviderCommissionRuleService service;

    @MockitoSpyBean
    private ShortDramaProviderMapper providerMapper;

    @Autowired
    private ProviderCommissionRuleMapper ruleMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("同一平台并发创建重叠规则最多成功一条")
    void concurrentCreateSerializesOnProviderRow() throws Exception {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        LocalDateTime from = LocalDateTime.of(2099, 9, 1, 0, 0);
        CountDownLatch providerLocked = new CountDownLatch(1);
        CountDownLatch secondLockAttempted = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        AtomicReference<Thread> secondThread = new AtomicReference<>();
        Answer<?> providerMapperDelegate = mockingDetails(providerMapper)
                .getMockCreationSettings()
                .getDefaultAnswer();
        doAnswer(invocation -> {
            if (Thread.currentThread() != secondThread.get()) {
                return providerMapperDelegate.answer(invocation);
            }
            secondLockAttempted.countDown();
            Object provider = providerMapperDelegate.answer(invocation);
            secondLockAcquired.countDown();
            return provider;
        }).when(providerMapper).findByIdForUpdate(providerId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                providerMapper.findByIdForUpdate(providerId);
                providerLocked.countDown();
                assertThat(await(secondLockAttempted, 5, TimeUnit.SECONDS)).isTrue();
                assertThat(await(secondLockAcquired, 200, TimeUnit.MILLISECONDS))
                        .as("第二个事务不得在第一个事务提交前获得平台行锁")
                        .isFalse();
                service.create(1L, providerId, request(from));
                return null;
            }));
            if (!providerLocked.await(5, TimeUnit.SECONDS)) {
                first.get(1, TimeUnit.SECONDS);
                throw new AssertionError("first transaction did not acquire provider lock");
            }

            Future<?> second = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                return service.create(1L, providerId, request(from));
            });
            first.get(10, TimeUnit.SECONDS);
            assertThatThrownBusinessException(second);

            assertThat(ruleMapper.findAllByProviderId(providerId)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
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

    private boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
