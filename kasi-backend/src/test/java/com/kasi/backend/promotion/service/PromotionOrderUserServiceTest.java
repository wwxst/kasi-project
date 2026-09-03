package com.kasi.backend.promotion.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.dto.PromotionOrderMonthQueryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionOrderUserServiceTest extends BaseAuthTest {
    @Autowired
    private PromotionOrderUserService userService;

    @Test
    @DisplayName("用户月度佣金按支付月份聚合退款冲销且不泄漏其他用户订单")
    void monthlyQueryIsUserScopedAndRefundAware() {
        SeedIds ids = seedDependencies();
        insertOrder(ids, "own-paid", ids.primaryUserId(), "PAID", "CALCULATED", "4.79", "2025-07-01 10:00:00");
        insertOrder(ids, "own-unpaid", ids.primaryUserId(), "UNPAID", "NOT_APPLICABLE", null, "2025-07-01 09:00:00");
        insertOrder(ids, "own-refunded", ids.primaryUserId(), "REFUNDED", "REVERSED", "4.79", "2025-07-02 10:00:00");
        insertOrder(ids, "own-unknown", ids.primaryUserId(), "UNKNOWN", "NOT_APPLICABLE", null, "2025-07-01 08:00:00");
        insertOrder(ids, "other-paid", ids.otherUserId(), "PAID", "CALCULATED", "9.58", "2025-07-03 10:00:00");
        insertOrder(ids, "own-august", ids.primaryUserId(), "PAID", "CALCULATED", "4.79", "2025-08-01 10:00:00");
        PromotionOrderMonthQueryDTO query = new PromotionOrderMonthQueryDTO();
        query.setMonth("2025-07");

        var page = userService.getPage(ids.primaryUserId(), query);
        var monthly = userService.getMonthly(ids.primaryUserId(), query);

        assertThat(page.getList()).extracting("externalOrderId")
                .containsExactlyInAnyOrder("own-paid", "own-unpaid", "own-refunded");
        assertThat(monthly.getPaidOrderCount()).isEqualTo(1);
        assertThat(monthly.getCalculatedCommission()).isEqualByComparingTo("4.79");
        assertThat(monthly.getReversedCommission()).isEqualByComparingTo("4.79");
        assertThat(monthly.getNetCommission()).isEqualByComparingTo("0.00");
    }

    private SeedIds seedDependencies() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        Long primaryUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, PRIMARY_USER_NO);
        Long otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no=?", Long.class, MOBILE_USER_NO);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,currency) VALUES (?,'GoodShort','USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        return new SeedIds(providerId, connectionId, primaryUserId, otherUserId);
    }

    private void insertOrder(SeedIds ids, String orderId, Long userId, String status,
                             String commissionStatus, String commission, String paidAt) {
        jdbcTemplate.update("INSERT INTO promotion_order "
                        + "(connection_id,provider_id,external_order_id,order_amount_minor,order_amount,currency,"
                        + "raw_status,status,paid_at,user_id,attribution_status,commission_amount,commission_status,"
                        + "raw_payload_json,sync_start_date,sync_end_date,last_synced_at) "
                        + "VALUES (?,?,?,999,9.99,'USD',?,?,?,?,'ATTRIBUTED',?,?,?,"
                        + "'2025-07-01 00:00:00','2025-07-31 23:59:59',CURRENT_TIMESTAMP)",
                ids.connectionId(), ids.providerId(), orderId,
                "REFUNDED".equals(status) ? "3" : "PAID".equals(status) ? "1" : "UNPAID".equals(status) ? "0" : "9", status, paidAt, userId,
                commission == null ? null : new BigDecimal(commission), commissionStatus, "{\"orderId\":\"" + orderId + "\"}");
    }

    private record SeedIds(Long providerId, Long connectionId, Long primaryUserId, Long otherUserId) {
    }
}
