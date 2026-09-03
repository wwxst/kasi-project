package com.kasi.backend;

import com.kasi.backend.support.MySqlContractTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MySQL 8.4迁移结构一致性")
@EnabledIfEnvironmentVariable(
        named = "MYSQL_MIGRATION_URL",
        matches = ".+",
        disabledReason = "SKIP: MYSQL_MIGRATION_URL is not configured")
class MigrationSchemaParityMySqlContractIT extends MySqlContractTestSupport {

    @Test
    @DisplayName("开发初始化与Flyway迁移生成相同结构和固定数据")
    void developmentInitializationAndFlywayMigrationHaveTheSameResult() {
        JdbcTemplate migrationJdbc = migrationJdbcTemplate();

        assertQueryMatches(migrationJdbc, """
                SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_TYPE = 'BASE TABLE'
                  AND TABLE_NAME <> 'flyway_schema_history'
                ORDER BY TABLE_NAME
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE,
                       IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME <> 'flyway_schema_history'
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME <> 'flyway_schema_history'
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME <> 'flyway_schema_history'
                ORDER BY TABLE_NAME, CONSTRAINT_NAME
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT CONSTRAINT_NAME, CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                ORDER BY CONSTRAINT_NAME
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT provider_code, provider_name, status
                FROM short_drama_provider
                ORDER BY provider_code
                """);
        assertQueryMatches(migrationJdbc, """
                SELECT task_code, description, cycle_type, interval_value,
                       interval_hours_part, interval_minutes_part, time_of_day,
                       day_of_week, day_of_month, month_of_year, enabled,
                       lease_owner, lease_until
                FROM system_scheduled_task
                ORDER BY task_code
                """);
    }

    private void assertQueryMatches(JdbcTemplate migrationJdbc, String sql) {
        List<Map<String, Object>> developmentRows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> migrationRows = migrationJdbc.queryForList(sql);
        assertThat(migrationRows).containsExactlyElementsOf(developmentRows);
    }

    private JdbcTemplate migrationJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                requireEnvironment("MYSQL_MIGRATION_URL"),
                requireEnvironment("MYSQL_MIGRATION_USERNAME"),
                requireEnvironment("MYSQL_MIGRATION_PASSWORD"));
        return new JdbcTemplate(dataSource);
    }
}
