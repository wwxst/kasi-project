package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionOrderMigrationTest {

    @Test
    @DisplayName("初始化脚本创建订单和分佣规则历史表并约束供应方订单幂等键")
    void initializationCreatesOrderAndRuleHistoryTables() {
        JdbcTemplate jdbc = initializeDatabase("promotion_order");

        assertThat(tableCount(jdbc, "PROMOTION_ORDER")).isEqualTo(1);
        assertThat(tableCount(jdbc, "PROVIDER_COMMISSION_RULE_HISTORY")).isEqualTo(1);
        assertThat(columnCount(jdbc, "PROMOTION_ORDER",
                "RAW_PAYLOAD_JSON", "TRACKING_NO", "ATTRIBUTION_STATUS", "RULE_HISTORY_ID",
                "CHANNEL_FEE_RATE", "PRINCIPAL_FEE_RATE", "PRINCIPAL_COMMISSION_RATE",
                "DOWNSTREAM_FEE_RATE", "DOWNSTREAM_COMMISSION_RATE", "COMMISSION_AMOUNT"))
                .isEqualTo(10);

        seedOrderDependencies(jdbc);
        insertOrder(jdbc, "external-order-1");
        assertThatThrownBy(() -> insertOrder(jdbc, "external-order-1"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME=?", Integer.class, table);
    }

    private int columnCount(JdbcTemplate jdbc, String table, String... columns) {
        String placeholders = String.join(",", java.util.Collections.nCopies(columns.length, "?"));
        Object[] args = new Object[columns.length + 1];
        args[0] = table;
        System.arraycopy(columns, 0, args, 1, columns.length);
        return jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME=? AND COLUMN_NAME IN (" + placeholders + ")",
                Integer.class, args);
    }

    private void seedOrderDependencies(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO promotion_user (user_no,password) VALUES ('100000000001','hash')");
        jdbc.update("INSERT INTO short_drama_connection "
                + "(provider_id,connection_name,currency) VALUES (1,'default','USD')");
        jdbc.update("INSERT INTO provider_drama "
                + "(connection_id,external_drama_id,title,language) VALUES (1,'book-1','Drama','ENGLISH')");
        jdbc.update("INSERT INTO promotion_link "
                + "(user_id,provider_id,connection_id,drama_id,batch_no,media_type,link_variant,request_key,tracking_no,status) "
                + "VALUES (1,1,1,1,'batch-1','TIKTOK','LANDING','request-1','tracking-1','SUCCESS')");
    }

    private void insertOrder(JdbcTemplate jdbc, String externalOrderId) {
        jdbc.update("INSERT INTO promotion_order "
                        + "(connection_id,provider_id,external_order_id,order_amount_minor,order_amount,currency,"
                        + "raw_status,status,attribution_status,raw_payload_json,sync_start_date,sync_end_date,"
                        + "last_synced_at) VALUES (?,?,?,?,?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP)",
                1L, 1L, externalOrderId, 999L, new BigDecimal("9.99"), "USD", "1", "PAID",
                "UNATTRIBUTED", "{}", "2025-07-01 00:00:00", "2025-07-01 23:59:59");
    }

}
