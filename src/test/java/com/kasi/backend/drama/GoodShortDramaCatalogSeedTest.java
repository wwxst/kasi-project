package com.kasi.backend.drama;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodShortDramaCatalogSeedTest {

    private static final String SEED_SCRIPT = "scripts/dev/seed_goodshort_drama_catalog.sql";

    @Test
    @DisplayName("本地 GoodShort 种子可重复执行且保持目录 ID 稳定")
    void seedIsIdempotentAndProducesExpectedCatalog() {
        JdbcTemplate jdbc = migrateAllMigrations();

        executeSeed(jdbc);
        Map<String, Long> dramaIdsBefore = jdbc.queryForList(
                        "SELECT external_drama_id, id FROM provider_drama ORDER BY external_drama_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_DRAMA_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        Map<String, Long> contentIdsBefore = jdbc.queryForList(
                        "SELECT external_content_id, id FROM provider_drama_content ORDER BY external_content_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_CONTENT_ID"),
                        row -> ((Number) row.get("ID")).longValue()));

        executeSeed(jdbc);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM short_drama_connection", Long.class)).isEqualTo(1L);
        Map<String, Object> connection = jdbc.queryForMap(
                "SELECT connection_name, currency, status, filing_mode, partner_id, api_key_ciphertext, base_url "
                        + "FROM short_drama_connection");
        assertThat(connection.get("CONNECTION_NAME")).isEqualTo("GoodShort 本地假数据");
        assertThat(connection.get("CURRENCY")).isEqualTo("USD");
        assertThat(((Number) connection.get("STATUS")).intValue()).isZero();
        assertThat(connection.get("FILING_MODE")).isEqualTo("MANUAL");
        assertThat(connection.get("PARTNER_ID")).isNull();
        assertThat(connection.get("API_KEY_CIPHERTEXT")).isNull();
        assertThat(connection.get("BASE_URL")).isNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class)).isEqualTo(24L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama_content", Long.class)).isEqualTo(204L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_sync_checkpoint", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_sync_checkpoint WHERE status = 'SUCCESS'", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_sync_checkpoint WHERE last_error_code IS NULL "
                        + "AND last_error_message IS NULL AND lease_owner IS NULL AND lease_until IS NULL", Long.class))
                .isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'ENGLISH'", Long.class)).isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'SPANISH'", Long.class)).isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT local_status) FROM provider_drama", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForList("SELECT local_status FROM provider_drama GROUP BY local_status", String.class))
                .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "OFFLINE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE cover_url IS NULL", Long.class)).isGreaterThan(0L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama_content WHERE is_free = 0", Long.class)).isGreaterThan(0L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama_content WHERE is_free = 1", Long.class)).isGreaterThan(0L);
        assertThat(jdbc.queryForObject(
                "SELECT MIN(episode_count) FROM (SELECT drama_id, COUNT(*) episode_count "
                        + "FROM provider_drama_content GROUP BY drama_id)", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT MAX(episode_count) FROM (SELECT drama_id, COUNT(*) episode_count "
                        + "FROM provider_drama_content GROUP BY drama_id)", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama_content WHERE sequence_no <= 2 AND is_free = 1", Long.class))
                .isEqualTo(48L);
        List<Integer> episodeCounts = jdbc.queryForList(
                "SELECT COUNT(*) FROM provider_drama_content GROUP BY drama_id ORDER BY drama_id", Integer.class);
        assertThat(episodeCounts).hasSize(24);
        assertThat(episodeCounts).containsExactly(
                5, 6, 7, 8, 9, 10, 11, 12,
                5, 6, 7, 8, 9, 10, 11, 12,
                5, 6, 7, 8, 9, 10, 11, 12);

        Map<String, Long> dramaIdsAfter = jdbc.queryForList(
                        "SELECT external_drama_id, id FROM provider_drama ORDER BY external_drama_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_DRAMA_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        Map<String, Long> contentIdsAfter = jdbc.queryForList(
                        "SELECT external_content_id, id FROM provider_drama_content ORDER BY external_content_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_CONTENT_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        assertThat(dramaIdsAfter).isEqualTo(dramaIdsBefore);
        assertThat(contentIdsAfter).isEqualTo(contentIdsBefore);
    }

    @Test
    @DisplayName("存在真实 GoodShort 接入时本地种子拒绝写入")
    void seedRejectsExistingRealConnectionWithoutWritingFixtures() {
        JdbcTemplate jdbc = migrateAllMigrations();
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency, status, filing_mode, base_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                providerId, "GoodShort Real", "partner-live", "cipher-live", "USD", 1, "API", "https://api.goodshort.com");

        assertThatThrownBy(() -> executeSeed(jdbc)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class)).isZero();
        Map<String, Object> connection = jdbc.queryForMap(
                "SELECT partner_id, status FROM short_drama_connection WHERE provider_id = ?", providerId);
        assertThat(connection.get("PARTNER_ID")).isEqualTo("partner-live");
        assertThat(((Number) connection.get("STATUS")).intValue()).isEqualTo(1);
    }

    private static void executeSeed(JdbcTemplate jdbc) {
        FileSystemResource resource = new FileSystemResource(SEED_SCRIPT);
        jdbc.execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection, resource);
            return null;
        });
    }

    private static JdbcTemplate migrateAllMigrations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:goodshort_seed_" + UUID.randomUUID()
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
}
