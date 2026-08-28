package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommissionRuleMigrationTest {

    @Test
    @DisplayName("初始化脚本直接定义最终分佣规则结构而不包含旧版清理")
    void initializationDefinesFinalCommissionRuleShapeWithoutLegacyCleanup() throws Exception {
        String initialization = new ClassPathResource("db/kasi_promotion.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(initialization)
                .contains("UNIQUE KEY `uk_provider_commission_provider` (`provider_id`)")
                .doesNotContain("effective_from")
                .doesNotContain("effective_to")
                .doesNotContain("idx_provider_commission_time")
                .doesNotContain("provider_commission_rule_keep");
    }

    @Test
    @DisplayName("推广链接外键列与关联主键使用相同的无符号类型")
    void promotionLinkForeignKeysMatchUnsignedPrimaryKeys() throws Exception {
        String initialization = new ClassPathResource("db/kasi_promotion.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(initialization)
                .contains("`user_id` BIGINT UNSIGNED NOT NULL")
                .contains("`provider_id` BIGINT UNSIGNED NOT NULL")
                .contains("`connection_id` BIGINT UNSIGNED NOT NULL")
                .contains("`drama_id` BIGINT UNSIGNED NOT NULL")
                .doesNotContain("`media_account_id` BIGINT UNSIGNED NOT NULL COMMENT '推广用户媒体账号 ID'");
    }

    @Test
    @DisplayName("初始化脚本创建每个平台唯一规则并保存高精度费率")
    void initializationCreatesProviderCommissionRuleSchema() {
        JdbcTemplate jdbc = initializeDatabase("provider_commission");
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);

        jdbc.update("INSERT INTO provider_commission_rule "
                        + "(provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,created_by,updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                providerId, new BigDecimal("0.3000000000"), BigDecimal.ZERO,
                new BigDecimal("0.8000000000"), BigDecimal.ZERO,
                new BigDecimal("0.7000000000"), 1L, 1L);

        assertThat(jdbc.queryForObject(
                "SELECT channel_fee_rate FROM provider_commission_rule", BigDecimal.class))
                .isEqualByComparingTo("0.3000000000");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME='PROVIDER_COMMISSION_RULE'", Integer.class))
                .isEqualTo(11);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME='PROVIDER_COMMISSION_RULE' AND COLUMN_NAME IN ('EFFECTIVE_FROM','EFFECTIVE_TO')",
                Integer.class)).isZero();
    }

}
