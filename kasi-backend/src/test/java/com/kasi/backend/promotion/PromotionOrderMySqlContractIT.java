package com.kasi.backend.promotion;

import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.PromotionOrderService;
import com.kasi.backend.promotion.service.PromotionOrderUpsertResult;
import com.kasi.backend.provider.spi.ProviderOrderRecord;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import com.kasi.backend.support.MySqlContractTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "MYSQL_CONTRACT_URL",
        matches = ".+",
        disabledReason = "SKIP: MYSQL_CONTRACT_URL is not configured")
@DisplayName("MySQL订单幂等契约")
class PromotionOrderMySqlContractIT extends MySqlContractTestSupport {

    @Autowired
    private PromotionOrderService orderService;

    @Autowired
    private PromotionOrderMapper orderMapper;

    @Test
    @DisplayName("并发同步同一平台订单只插入一次并返回一条已有结果")
    void concurrentUpsertOfSameOrderCreatesOneRow() throws Exception {
        Long connectionId = insertConnection("order-idempotency");
        Long dramaId = insertDrama(connectionId, "order-idempotency");
        insertPromotionLink(connectionId, dramaId, "order-idempotency");
        insertCommissionHistory();

        String externalOrderId = CONTRACT_PREFIX + "order-idempotency";
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 1, 12, 0);
        ProviderOrderRecord record = new ProviderOrderRecord(
                externalOrderId, "external-user", 1000L, new BigDecimal("10.00"), "USD", paidAt,
                ProviderOrderStatus.PAID, "PAID", primaryUserNo(),
                CONTRACT_PREFIX + "order-idempotency", "search", "channel", "partner",
                paidAt, "{\"orderId\":\"" + externalOrderId + "\"}");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<PromotionOrderUpsertResult> first = executor.submit(
                    () -> upsertAfterStart(connectionId, record, ready, start));
            Future<PromotionOrderUpsertResult> second = executor.submit(
                    () -> upsertAfterStart(connectionId, record, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> inserted = List.of(
                    first.get(10, TimeUnit.SECONDS).inserted(),
                    second.get(10, TimeUnit.SECONDS).inserted());
            assertThat(inserted).containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM promotion_order
                WHERE connection_id = ? AND external_order_id = ?
                """, Long.class, connectionId, externalOrderId)).isEqualTo(1L);
        PromotionOrder stored = orderMapper.findBySource(connectionId, externalOrderId);
        assertThat(stored.getUserId()).isEqualTo(primaryUserId());
        assertThat(stored.getCommissionAmount()).isEqualByComparingTo("3.76");
    }

    private PromotionOrderUpsertResult upsertAfterStart(Long connectionId,
                                                        ProviderOrderRecord record,
                                                        CountDownLatch ready,
                                                        CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent order upsert did not start in time");
        }
        LocalDateTime syncEnd = LocalDateTime.of(2026, 9, 2, 0, 0);
        return orderService.upsert(runtime(connectionId), record, syncEnd.minusDays(1), syncEnd);
    }
}
