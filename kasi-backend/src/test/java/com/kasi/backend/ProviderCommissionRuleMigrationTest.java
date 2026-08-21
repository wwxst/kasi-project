package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommissionRuleMigrationTest {

    @Test
    @DisplayName("V8创建短剧平台分佣规则表并保存高精度费率")
    void migrateCreatesProviderCommissionRuleSchema() {
        JdbcTemplate jdbc = migrateAllMigrations();
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);

        jdbc.update("INSERT INTO provider_commission_rule "
                        + "(provider_id,channel_fee_rate,principal_fee_rate,principal_commission_rate,"
                        + "downstream_fee_rate,downstream_commission_rate,effective_from,created_by,updated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                providerId, new BigDecimal("0.3000000000"), BigDecimal.ZERO,
                new BigDecimal("0.8000000000"), BigDecimal.ZERO,
                new BigDecimal("0.7000000000"), LocalDateTime.of(2026, 9, 1, 0, 0), 1L, 1L);

        assertThat(jdbc.queryForObject(
                "SELECT channel_fee_rate FROM provider_commission_rule", BigDecimal.class))
                .isEqualByComparingTo("0.3000000000");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME='PROVIDER_COMMISSION_RULE'", Integer.class))
                .isEqualTo(13);
    }

    private JdbcTemplate migrateAllMigrations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:provider_commission_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
