package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class PromotionLinkMigrationTest {

    @Test
    @DisplayName("初始化脚本创建推广链接表并约束用户幂等键和追踪号")
    void initializationCreatesPromotionLinkTable() {
        JdbcTemplate jdbc = initializeDatabase("promotion_link");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROMOTION_LINK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROMOTION_LINK' AND COLUMN_NAME = 'STATUS'",
                String.class)).contains("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME='PROMOTION_LINK' AND COLUMN_NAME IN ('BATCH_NO','MEDIA_TYPE','LINK_VARIANT')", Integer.class)).isEqualTo(3);

        jdbc.update("INSERT INTO promotion_user (user_no, password) VALUES ('100000000001', 'hash')");
        jdbc.update("MERGE INTO short_drama_provider (provider_code, provider_name) KEY (provider_code) VALUES ('GOODSHORT', 'GoodShort')");
        jdbc.update("INSERT INTO short_drama_connection (provider_id, connection_name, currency) VALUES (1, 'default', 'USD')");
        jdbc.update("INSERT INTO provider_drama (connection_id, external_drama_id, title, language) VALUES (1, 'book-1', 'Drama', 'ENGLISH')");
        jdbc.update("INSERT INTO promotion_link (user_id, provider_id, connection_id, drama_id, batch_no, media_type, link_variant, request_key, tracking_no, status) VALUES (1, 1, 1, 1, 'batch-1', 'TIKTOK', 'LANDING', 'request-1', 'tracking-1', 'PENDING')");
        assertThat(jdbc.queryForObject("SELECT status FROM promotion_link WHERE id = 1", String.class))
                .isEqualTo("PENDING");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO promotion_link (user_id, provider_id, connection_id, drama_id, batch_no, media_type, link_variant, request_key, tracking_no, status) VALUES (1, 1, 1, 1, 'batch-1', 'TIKTOK', 'LANDING', 'request-1', 'tracking-2', 'PENDING')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

}
