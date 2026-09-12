package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@Tag("integration")
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
        assertThat(columnExists(jdbc, "SHORT_DRAMA_CONNECTION", "API_FILING_MEDIA_TYPES")).isTrue();
        assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "FILING_METHOD")).isTrue();
        assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "LAST_SUBMIT_ATTEMPT_AT")).isTrue();
        assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "MANUAL_UPDATED_BY")).isTrue();
        assertThat(columnExists(jdbc, "PROVIDER_MEDIA_FILING", "MANUAL_UPDATED_AT")).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' AND COLUMN_NAME = 'FILING_MODE'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PROVIDER_MEDIA_FILING' AND COLUMN_NAME = 'OPERATE_BY'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' AND COLUMN_NAME IN ('BASE_URL', 'PARTNER_ID', 'API_KEY_CIPHERTEXT') AND IS_NULLABLE = 'YES'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'SHORT_DRAMA_CONNECTION' AND COLUMN_NAME = 'MEDIA_ROOT_DOMAIN' AND IS_NULLABLE = 'YES'",
                Integer.class)).isEqualTo(1);
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
                "SELECT base_url FROM short_drama_connection WHERE id = ?", String.class, connectionId))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT api_filing_media_types FROM short_drama_connection WHERE id = ?",
                String.class, connectionId)).isEqualTo("[\"FACEBOOK\"]");
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
                .isEqualTo("NOT_SUBMITTED");
        assertThat(jdbc.queryForObject(
                "SELECT filing_method FROM provider_media_filing WHERE media_account_id = ?", String.class, mediaId))
                .isEqualTo("API");
        assertThat(jdbc.queryForObject(
                "SELECT task_data_version FROM provider_media_filing WHERE media_account_id = ?", Integer.class, mediaId))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO promotion_media_account "
                + "(user_id, media_type, external_account_id) VALUES (?, 'TIKTOK', 'creator-1')", userId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update("DELETE FROM promotion_user WHERE id = ?", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_user WHERE id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_media_account WHERE id = ?", Long.class, mediaId))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("V1到V9按历史证据迁移五状态和报白方式")
    void migrationsMapExistingFilingsToFiveStatusesAndMethods() {
        JdbcTemplate jdbc = initializeDatabase("media_account_filing_v9", "db/migration/V1__baseline.sql");
        for (int version = 2; version <= 8; version++) {
            applyMigration(jdbc, migrationPath(version));
        }

        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO promotion_user (user_no, password) VALUES ('100000000002', 'hash')");
        Long userId = jdbc.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = '100000000002'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency) "
                        + "VALUES (?, 'GoodShort迁移接入', 'partner-v9', 'ciphertext', 'USD')",
                providerId);
        Long connectionId = jdbc.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);

        insertFiling(jdbc, userId, connectionId, "FACEBOOK", "approved", "FAILED", "1", null, "old error");
        insertFiling(jdbc, userId, connectionId, "TIKTOK", "rejected", "FAILED", "2", null, "old error");
        insertFiling(jdbc, userId, connectionId, "YOUTUBE", "pending-manual", "FAILED", null,
                "2026-09-09 10:00:00", "query error");
        insertFiling(jdbc, userId, connectionId, "INSTAGRAM", "submit-failed", "FAILED", null,
                null, "report rejected");
        insertFiling(jdbc, userId, connectionId, "TIKTOK", "not-submitted-manual", "PENDING", null,
                null, null);
        insertFiling(jdbc, userId, connectionId, "FACEBOOK", "pending-api", "FAILED", null,
                "2026-09-09 11:00:00", "query error");

        applyMigration(jdbc, "db/migration/V9__media_filing_api_manual.sql");

        assertThat(jdbc.queryForObject(
                "SELECT api_filing_media_types FROM short_drama_connection WHERE id = ?",
                String.class, connectionId)).isEqualTo("[\"FACEBOOK\"]");
        List<Map<String, Object>> filings = jdbc.queryForList("""
                SELECT a.external_account_id, f.filing_method, f.status, f.next_action,
                       f.next_action_at, f.retry_count, f.lease_owner, f.lease_until
                FROM provider_media_filing f
                JOIN promotion_media_account a ON a.id = f.media_account_id
                ORDER BY a.external_account_id
                """);
        assertThat(filings)
                .extracting(
                        row -> row.get("EXTERNAL_ACCOUNT_ID"),
                        row -> row.get("FILING_METHOD"),
                        row -> row.get("STATUS"),
                        row -> row.get("NEXT_ACTION"),
                        row -> row.get("RETRY_COUNT"),
                        row -> row.get("LEASE_OWNER"),
                        row -> row.get("LEASE_UNTIL"))
                .containsExactly(
                        tuple("approved", "API", "APPROVED", "NONE", 0, null, null),
                        tuple("not-submitted-manual", "MANUAL", "NOT_SUBMITTED", "NONE", 0, null, null),
                        tuple("pending-api", "API", "PENDING", "QUERY", 0, null, null),
                        tuple("pending-manual", "MANUAL", "PENDING", "NONE", 0, null, null),
                        tuple("rejected", "MANUAL", "REJECTED", "NONE", 0, null, null),
                        tuple("submit-failed", "MANUAL", "SUBMIT_FAILED", "NONE", 0, null, null));
        assertThat(filings)
                .filteredOn(row -> row.get("EXTERNAL_ACCOUNT_ID").equals("pending-api"))
                .singleElement()
                .satisfies(row -> assertThat(row.get("NEXT_ACTION_AT")).isNotNull());
        assertThat(filings)
                .filteredOn(row -> !row.get("EXTERNAL_ACCOUNT_ID").equals("pending-api"))
                .allSatisfy(row -> assertThat(row.get("NEXT_ACTION_AT")).isNull());
    }

    private static void insertFiling(JdbcTemplate jdbc, Long userId, Long connectionId,
                                     String mediaType, String externalAccountId, String status,
                                     String remoteStatus, String lastSubmittedAt, String errorMessage) {
        jdbc.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id) VALUES (?, ?, ?)",
                userId, mediaType, externalAccountId);
        Long mediaAccountId = jdbc.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = ?",
                Long.class, externalAccountId);
        jdbc.update("""
                INSERT INTO provider_media_filing
                    (connection_id, media_account_id, status, remote_status, last_submitted_at,
                     last_error_message, next_action, next_action_at, retry_count, lease_owner, lease_until)
                VALUES (?, ?, ?, ?, ?, ?, 'QUERY', CURRENT_TIMESTAMP, 3, 'old-worker', CURRENT_TIMESTAMP)
                """, connectionId, mediaAccountId, status, remoteStatus, lastSubmittedAt, errorMessage);
    }

    private static String migrationPath(int version) {
        return switch (version) {
            case 2 -> "db/migration/V2__promotion_analytical_report.sql";
            case 3 -> "db/migration/V3__normalize_drama_sync_page_size.sql";
            case 4 -> "db/migration/V4__remove_manual_media_filing.sql";
            case 5 -> "db/migration/V5__split_goodshort_order_sync_tasks.sql";
            case 6 -> "db/migration/V6__promotion_project.sql";
            case 7 -> "db/migration/V7__user_profile_contacts.sql";
            case 8 -> "db/migration/V8__user_student_type.sql";
            default -> throw new IllegalArgumentException("Unsupported migration version: " + version);
        };
    }

    private static void applyMigration(JdbcTemplate jdbc, String path) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource(path));
        populator.execute(jdbc.getDataSource());
    }

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private static boolean columnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
