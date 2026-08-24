package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionLinkMigrationTest {

    @Test
    @DisplayName("V1创建推广链接表并约束用户幂等键和追踪号")
    void migrationCreatesPromotionLinkTable() {
        JdbcTemplate jdbc = migrateAll();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROMOTION_LINK'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROMOTION_LINK' AND COLUMN_NAME = 'STATUS'",
                String.class)).contains("PENDING");

        jdbc.update("INSERT INTO promotion_user (user_no, password) VALUES ('100000000001', 'hash')");
        jdbc.update("MERGE INTO short_drama_provider (provider_code, provider_name) KEY (provider_code) VALUES ('GOODSHORT', 'GoodShort')");
        jdbc.update("INSERT INTO short_drama_connection (provider_id, connection_name, currency) VALUES (1, 'default', 'USD')");
        jdbc.update("INSERT INTO promotion_media_account (user_id, media_type, external_account_id) VALUES (1, 'TIKTOK', 'media-1')");
        jdbc.update("INSERT INTO provider_drama (connection_id, external_drama_id, title, language) VALUES (1, 'book-1', 'Drama', 'ENGLISH')");
        jdbc.update("INSERT INTO promotion_link (user_id, provider_id, connection_id, drama_id, media_account_id, request_key, tracking_no, provider_code, status) VALUES (1, 1, 1, 1, 1, 'request-1', 'tracking-1', 'GOODSHORT', 'PENDING')");
        assertThat(jdbc.queryForObject("SELECT status FROM promotion_link WHERE id = 1", String.class))
                .isEqualTo("PENDING");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO promotion_link (user_id, provider_id, connection_id, drama_id, media_account_id, request_key, tracking_no, provider_code, status) VALUES (1, 1, 1, 1, 1, 'request-1', 'tracking-2', 'GOODSHORT', 'PENDING')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private static JdbcTemplate migrateAll() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:promotion_link_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
