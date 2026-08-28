package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

class DramaDownloadTaskMigrationTest {

    @Test
    @DisplayName("创建短剧下载任务表并提供状态、进度和文件字段")
    void initializationCreatesDramaDownloadTaskTable() {
        JdbcTemplate jdbc = initializeDatabase("drama_download");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'DRAMA_DOWNLOAD_TASK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'DRAMA_DOWNLOAD_TASK' "
                        + "AND COLUMN_NAME = 'STATUS'",
                String.class)).contains("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'DRAMA_DOWNLOAD_TASK' "
                        + "AND COLUMN_NAME IN ('USER_ID','DRAMA_ID','CONTENT_IDS_JSON','FILE_PATH',"
                        + "'TOTAL_COUNT','COMPLETED_COUNT','EXPIRES_AT')",
                Integer.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("测试 schema 保留下载任务索引和级联删除契约")
    void testSchemaPreservesDownloadTaskIndexesWithoutCascadeDeletes() {
        JdbcTemplate jdbc = initializeDatabase("drama_download_test_schema", "test-schema.sql");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'DRAMA_DOWNLOAD_TASK'
                AND INDEX_NAME IN ('IDX_DRAMA_DOWNLOAD_STATUS_EXPIRES')
                """, Integer.class)).isEqualTo(1);

        jdbc.update("INSERT INTO promotion_user (user_no, password) VALUES ('100000000001', 'hash')");
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection (provider_id, connection_name, currency) VALUES (?, 'default', 'USD')",
                providerId);
        jdbc.update("INSERT INTO provider_drama (connection_id, external_drama_id, title, language) VALUES (1, 'book-1', 'Drama', 'ENGLISH')");
        jdbc.update("INSERT INTO drama_download_task (user_id, drama_id, content_ids_json, expires_at) VALUES (1, 1, '[]', CURRENT_TIMESTAMP)");

        jdbc.update("DELETE FROM provider_drama WHERE id = 1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drama_download_task", Integer.class)).isEqualTo(1);
    }

}
