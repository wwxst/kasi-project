package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskMigrationTest {

    @Test
    @DisplayName("V1创建定时任务配置并植入GoodShort增量同步任务")
    void migrateCreatesScheduledTaskConfig() {
        JdbcTemplate jdbc = migrateAllMigrations();

        assertThat(tableExists(jdbc, "SYSTEM_SCHEDULED_TASK")).isTrue();
        Map<String, Object> task = jdbc.queryForMap("""
                SELECT task_code, title, description, cycle_type, interval_value,
                       interval_hours_part, interval_minutes_part, interval_minutes, enabled
                FROM system_scheduled_task
                WHERE task_code = 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'
                """);
        assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_DRAMA_INCREMENTAL_SYNC");
        assertThat(task.get("TITLE")).isEqualTo("GoodShort 短剧增量同步");
        assertThat(task.get("DESCRIPTION")).isEqualTo("每隔60分钟执行一次GoodShort短剧目录增量同步");
        assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
        assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(60);
        assertThat(((Number) task.get("INTERVAL_HOURS_PART")).intValue()).isZero();
        assertThat(((Number) task.get("INTERVAL_MINUTES_PART")).intValue()).isZero();
        assertThat(((Number) task.get("INTERVAL_MINUTES")).intValue()).isEqualTo(60);
        assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("V16新增每分钟执行一次的GoodShort订单同步任务")
    void migrationCreatesGoodShortOrderSyncTask() {
        JdbcTemplate jdbc = migrateAllMigrations();

        Map<String, Object> task = jdbc.queryForMap("""
                SELECT task_code, title, description, cycle_type, interval_value,
                       interval_minutes, enabled, next_run_at
                FROM system_scheduled_task
                WHERE task_code = 'GOODSHORT_ORDER_SYNC'
                """);
        assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_ORDER_SYNC");
        assertThat(task.get("TITLE")).isEqualTo("GoodShort 订单同步");
        assertThat(task.get("DESCRIPTION")).isEqualTo("每隔1分钟同步最近3天的GoodShort订单");
        assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
        assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(1);
        assertThat(((Number) task.get("INTERVAL_MINUTES")).intValue()).isEqualTo(5);
        assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
        assertThat(task.get("NEXT_RUN_AT")).isNotNull();
    }

    private static JdbcTemplate migrateAllMigrations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:scheduled_task_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return new JdbcTemplate(dataSource);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }
}
