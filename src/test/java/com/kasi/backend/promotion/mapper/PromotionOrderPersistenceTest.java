package com.kasi.backend.promotion.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionOrderPersistenceTest extends BaseAuthTest {
    @Autowired
    private PromotionOrderMapper orderMapper;

    @Test
    @DisplayName("订单按连接和供应方订单号幂等更新且退款不覆盖佣金快照")
    void orderUpsertPreservesCommissionSnapshot() {
        PromotionOrder order = order("external-order-1", PromotionOrderStatus.PAID);
        order.setRuleHistoryId(seedRuleHistory());
        order.setChannelFeeRate(new BigDecimal("0.1000000000"));
        order.setPrincipalFeeRate(new BigDecimal("0.0200000000"));
        order.setPrincipalCommissionRate(new BigDecimal("0.8000000000"));
        order.setDownstreamFeeRate(new BigDecimal("0.0300000000"));
        order.setDownstreamCommissionRate(new BigDecimal("0.7000000000"));
        order.setCommissionAmount(new BigDecimal("4.79"));
        order.setCommissionStatus(PromotionCommissionStatus.CALCULATED);

        assertThat(orderMapper.insert(order)).isEqualTo(1);
        PromotionOrder stored = orderMapper.findBySource(order.getConnectionId(), "external-order-1");
        assertThat(stored.getCommissionAmount()).isEqualByComparingTo("4.79");

        stored.setStatus(PromotionOrderStatus.REFUNDED);
        stored.setRawStatus("3");
        stored.setProviderUpdatedAt(LocalDateTime.of(2025, 7, 2, 8, 0));
        stored.setRawPayloadJson("{\"payStatus\":3}");
        stored.setLastSyncedAt(LocalDateTime.of(2025, 7, 2, 8, 1));
        assertThat(orderMapper.updateSourceFields(stored)).isEqualTo(1);
        assertThat(orderMapper.markCommissionReversed(stored.getId())).isEqualTo(1);

        PromotionOrder refunded = orderMapper.findBySource(order.getConnectionId(), "external-order-1");
        assertThat(refunded.getStatus()).isEqualTo(PromotionOrderStatus.REFUNDED);
        assertThat(refunded.getCommissionStatus()).isEqualTo(PromotionCommissionStatus.REVERSED);
        assertThat(refunded.getCommissionAmount()).isEqualByComparingTo("4.79");
        assertThat(refunded.getDownstreamCommissionRate()).isEqualByComparingTo("0.7000000000");
    }

    private PromotionOrder order(String externalOrderId, PromotionOrderStatus status) {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id,connection_name,base_url,partner_id,api_key_ciphertext,currency,status) "
                        + "VALUES (?,'GoodShort','https://api.test','partner-1','cipher','USD',1)", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        PromotionOrder order = new PromotionOrder();
        order.setConnectionId(connectionId);
        order.setProviderId(providerId);
        order.setExternalOrderId(externalOrderId);
        order.setOrderAmountMinor(999L);
        order.setOrderAmount(new BigDecimal("9.99"));
        order.setCurrency("USD");
        order.setRawStatus(status == PromotionOrderStatus.PAID ? "1" : "0");
        order.setStatus(status);
        order.setPaidAt(LocalDateTime.of(2025, 7, 1, 15, 55, 30));
        order.setCustomParams("tracking-1");
        order.setAttributionStatus(PromotionAttributionStatus.UNATTRIBUTED);
        order.setRawPayloadJson("{\"orderId\":\"" + externalOrderId + "\"}");
        order.setSyncStartDate(LocalDateTime.of(2025, 7, 1, 0, 0));
        order.setSyncEndDate(LocalDateTime.of(2025, 7, 1, 23, 59, 59));
        order.setLastSyncedAt(LocalDateTime.of(2025, 7, 2, 8, 0));
        return order;
    }

    private Long seedRuleHistory() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username=?", Long.class, ADMIN_USERNAME);
        jdbcTemplate.update("INSERT INTO provider_commission_rule "
                        + "(provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,created_by,updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?)", providerId, new BigDecimal("0.1000000000"),
                new BigDecimal("0.0200000000"), new BigDecimal("0.8000000000"),
                new BigDecimal("0.0300000000"), new BigDecimal("0.7000000000"), adminId, adminId);
        Long ruleId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_commission_rule WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_commission_rule_history "
                        + "(provider_id,rule_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,created_by) VALUES (?,?,?,?,?,?,?,?)",
                providerId, ruleId, new BigDecimal("0.1000000000"), new BigDecimal("0.0200000000"),
                new BigDecimal("0.8000000000"), new BigDecimal("0.0300000000"),
                new BigDecimal("0.7000000000"), adminId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM provider_commission_rule_history WHERE provider_id=?", Long.class, providerId);
    }
}
