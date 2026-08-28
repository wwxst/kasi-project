package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaAccountFilingMigrationTest {

    @Test
    @DisplayName("初始化脚本创建平台接入、媒体账号、报备表及接口域名")
    void initializationCreatesMediaAccountFilingSchemaAndBaseUrl() {
        JdbcTemplate jdbc = initializeDatabase("media_account_filing");

        assertThat(tableExists(jdbc, "SHORT_DRAMA_PROVIDER")).isTrue();
        assertThat(tableExists(jdbc, "SHORT_DRAMA_CONNECTION")).isTrue();
        assertThat(tableExists(jdbc, "PROMOTION_MEDIA_ACCOUNT")).isTrue();
        assertThat(tableExists(jdbc, "PROVIDER_MEDIA_FILING")).isTrue();
        assertThat(tableExists(jdbc, "PROVIDER_DRAMA")).isTrue();
        assertThat(tableExists(jdbc, "PROVIDER_DRAMA_CONTENT")).isTrue();
        assertThat(tableExists(jdbc, "PROVIDER_SYNC_CHECKPOINT")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' AND COLUMN_NAME = 'FILING_MODE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PROVIDER_MEDIA_FILING' AND COLUMN_NAME = 'OPERATE_BY'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' AND COLUMN_NAME IN ('BASE_URL', 'PARTNER_ID', 'API_KEY_CIPHERTEXT') AND IS_NULLABLE = 'YES'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class))
                .isEqualTo(1L);

        jdbc.update("INSERT INTO promotion_user (user_no, password) VALUES (?, ?)",
                "100000000001", "hash");
        Long userId = jdbc.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = ?", Long.class, "100000000001");
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency) "
                        + "VALUES (?, ?, ?, ?, ?)",
                providerId, "GoodShort默认接入", "partner-1", "ciphertext", "USD");
        Long connectionId = jdbc.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
        assertThat(jdbc.queryForObject(
                "SELECT filing_mode FROM short_drama_connection WHERE id = ?", String.class, connectionId))
                .isEqualTo("API");
        assertThat(jdbc.queryForObject(
                "SELECT base_url FROM short_drama_connection WHERE id = ?", String.class, connectionId))
                .isNull();
        jdbc.update("INSERT INTO promotion_media_account "
                + "(user_id, media_type, external_account_id) VALUES (?, 'TIKTOK', 'creator-1')", userId);
        Long mediaId = jdbc.queryForObject("SELECT id FROM promotion_media_account", Long.class);
        jdbc.update("INSERT INTO provider_media_filing "
                + "(connection_id, media_account_id) VALUES (?, ?)", connectionId, mediaId);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM promotion_media_account WHERE id = ?", Number.class, mediaId)
                .intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM provider_media_filing WHERE media_account_id = ?", String.class, mediaId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT task_data_version FROM provider_media_filing WHERE media_account_id = ?", Integer.class, mediaId))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO promotion_media_account "
                + "(user_id, media_type, external_account_id) VALUES (?, 'TIKTOK', 'creator-1')", userId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM promotion_user WHERE id = ?", userId))
                .isInstanceOf(DataAccessException.class);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }
}
