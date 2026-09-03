package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ScheduledTaskMigrationTest {

    @Test
    @DisplayName("初始化脚本创建定时任务配置并植入GoodShort增量同步任务")
    void initializationCreatesScheduledTaskConfig() {
        JdbcTemplate jdbc = initializeDatabase("scheduled_task");

        assertThat(tableExists(jdbc, "SYSTEM_SCHEDULED_TASK")).isTrue();
        Map<String, Object> task = jdbc.queryForMap("""
                SELECT task_code, description, cycle_type, interval_value,
                       interval_hours_part, interval_minutes_part, enabled
                FROM system_scheduled_task
                WHERE task_code = 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'
                """);
        assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_DRAMA_INCREMENTAL_SYNC");
        assertThat(task.get("DESCRIPTION")).isEqualTo("每隔60分钟执行一次GoodShort短剧目录增量同步");
        assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
        assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(60);
        assertThat(((Number) task.get("INTERVAL_HOURS_PART")).intValue()).isZero();
        assertThat(((Number) task.get("INTERVAL_MINUTES_PART")).intValue()).isZero();
        assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("初始化脚本植入每分钟执行一次的GoodShort订单同步任务")
    void initializationCreatesGoodShortOrderSyncTask() {
        JdbcTemplate jdbc = initializeDatabase("scheduled_task");

        Map<String, Object> task = jdbc.queryForMap("""
                SELECT task_code, description, cycle_type, interval_value,
                       enabled, next_run_at
                FROM system_scheduled_task
                WHERE task_code = 'GOODSHORT_ORDER_SYNC'
                """);
        assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_ORDER_SYNC");
        assertThat(task.get("DESCRIPTION")).isEqualTo("每隔1分钟同步最近3天的GoodShort订单");
        assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
        assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(1);
        assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
        assertThat(task.get("NEXT_RUN_AT")).isNotNull();
    }

    @Test
    @DisplayName("初始化脚本植入每分钟执行一次的GoodShort免费剧集同步任务")
    void initializationCreatesGoodShortDramaContentSyncTask() {
        JdbcTemplate jdbc = initializeDatabase("scheduled_task");

        Map<String, Object> task = jdbc.queryForMap("""
                SELECT task_code, description, cycle_type, interval_value,
                       enabled, next_run_at
                FROM system_scheduled_task
                WHERE task_code = 'GOODSHORT_DRAMA_CONTENT_SYNC'
                """);
        assertThat(task.get("TASK_CODE")).isEqualTo("GOODSHORT_DRAMA_CONTENT_SYNC");
        assertThat(task.get("DESCRIPTION")).isEqualTo("每隔1分钟同步GoodShort免费剧集");
        assertThat(task.get("CYCLE_TYPE")).isEqualTo("INTERVAL_MINUTES");
        assertThat(((Number) task.get("INTERVAL_VALUE")).intValue()).isEqualTo(1);
        assertThat(((Number) task.get("ENABLED")).intValue()).isEqualTo(1);
        assertThat(task.get("NEXT_RUN_AT")).isNotNull();
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

}
