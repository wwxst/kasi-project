package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionOrderMigrationTest {

    @Test
    @DisplayName("V15 创建订单和分佣规则历史表并约束供应方订单幂等键")
    void migrationCreatesOrderAndRuleHistoryTables() {
        JdbcTemplate jdbc = migrateAll();

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

    @Test
    @DisplayName("V15 为迁移前已有的当前分佣规则生成初始历史快照")
    void migrationSeedsExistingCommissionRuleHistory() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("14").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO provider_commission_rule "
                        + "(provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,created_by,updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                providerId, new BigDecimal("0.1000000000"), new BigDecimal("0.0200000000"),
                new BigDecimal("0.8000000000"), new BigDecimal("0.0300000000"),
                new BigDecimal("0.7000000000"), 1L, 1L);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_commission_rule_history WHERE provider_id=?",
                Integer.class, providerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT downstream_commission_rate FROM provider_commission_rule_history WHERE provider_id=?",
                BigDecimal.class, providerId)).isEqualByComparingTo("0.7000000000");
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
        jdbc.update("INSERT INTO promotion_media_account "
                + "(user_id,media_type,external_account_id) VALUES (1,'TIKTOK','media-1')");
        jdbc.update("INSERT INTO provider_drama "
                + "(connection_id,external_drama_id,title,language) VALUES (1,'book-1','Drama','ENGLISH')");
        jdbc.update("INSERT INTO promotion_link "
                + "(user_id,provider_id,connection_id,drama_id,media_account_id,request_key,tracking_no,provider_code,status) "
                + "VALUES (1,1,1,1,1,'request-1','tracking-1','GOODSHORT','SUCCESS')");
    }

    private void insertOrder(JdbcTemplate jdbc, String externalOrderId) {
        jdbc.update("INSERT INTO promotion_order "
                        + "(connection_id,provider_id,external_order_id,order_amount_minor,order_amount,currency,"
                        + "raw_status,status,attribution_status,raw_payload_json,sync_start_date,sync_end_date,"
                        + "first_synced_at,last_synced_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                1L, 1L, externalOrderId, 999L, new BigDecimal("9.99"), "USD", "1", "PAID",
                "UNATTRIBUTED", "{}", "2025-07-01 00:00:00", "2025-07-01 23:59:59");
    }

    private JdbcTemplate migrateAll() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:promotion_order_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
