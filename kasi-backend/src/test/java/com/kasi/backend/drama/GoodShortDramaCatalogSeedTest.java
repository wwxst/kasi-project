package com.kasi.backend.drama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class GoodShortDramaCatalogSeedTest {

    private static final String SEED_SCRIPT = "scripts/dev/seed_goodshort_drama_catalog.sql";

    @Test
    @DisplayName("MySQL 临时表不可在同一内容 upsert 中重复引用以避免 1137 reopen 错误")
    void contentUpsertDoesNotReopenMySqlTemporaryTable() throws Exception {
        String script = Files.readString(Path.of(SEED_SCRIPT), StandardCharsets.UTF_8);
        Set<String> temporaryTables = parseTemporaryTableNames(script);
        Matcher matcher = Pattern.compile("(?is)INSERT\\s+INTO\\s+provider_drama_content\\b.*?;")
                .matcher(script);

        assertThat(temporaryTables).isNotEmpty();
        assertThat(temporaryTables)
                .contains("seed_goodshort_numbers", "seed_goodshort_episode_numbers", "seed_goodshort_guard");
        assertThat(matcher.find()).as("seed must contain the provider_drama_content upsert statement").isTrue();
        String contentUpsert = matcher.group().toLowerCase(Locale.ROOT);
        Set<String> referencedNumberTables = new LinkedHashSet<>();
        for (String temporaryTable : temporaryTables) {
            int references = countIdentifierReferences(contentUpsert, temporaryTable);
            assertThat(references)
                    .as("MySQL error 1137 forbids reopening TEMPORARY table '%s' in one statement", temporaryTable)
                    .isLessThanOrEqualTo(1);
            if (temporaryTable.contains("number") && references > 0) {
                referencedNumberTables.add(temporaryTable);
            }
        }

        assertThat(referencedNumberTables)
                .as("content upsert must reference two distinct ordinal temporary tables")
                .hasSize(2);
    }

    @Test
    @DisplayName("MySQL 临时表必须使用 temporary-only cleanup 避免隐式提交和持久表误删")
    void temporaryTablesUseTemporaryOnlyCleanup() throws Exception {
        String script = Files.readString(Path.of(SEED_SCRIPT), StandardCharsets.UTF_8);
        Set<String> temporaryTables = parseTemporaryTableNames(script);
        Set<String> ordinaryDrops = findMatches(script,
                Pattern.compile("(?im)^\\s*DROP\\s+TABLE\\s+`?(seed_goodshort_[a-z0-9_]+)`?\\s*;"));

        assertThat(ordinaryDrops)
                .as("seed cleanup must not use ordinary DROP TABLE for temporary tables")
                .isEmpty();

        Pattern temporaryCleanup = Pattern.compile(
                "(?is)/\\*!50000\\s+DROP\\s+TEMPORARY\\s+TABLE\\s+"
                        + "(?!IF\\s+EXISTS\\b)`?([a-z0-9_]+)`?\\s*\\*/\\s*;");
        Set<String> temporaryOnlyDrops = findMatches(script, temporaryCleanup);

        assertThat(script).doesNotContainPattern(
                "(?is)/\\*!50000\\s+DROP\\s+TEMPORARY\\s+TABLE\\s+IF\\s+EXISTS\\b");
        assertThat(temporaryOnlyDrops)
                .as("every declared temporary table must have MySQL temporary-only cleanup")
                .containsExactlyInAnyOrderElementsOf(temporaryTables);
    }

    @Test
    @DisplayName("本地 GoodShort 种子可重复执行且保持目录 ID 稳定")
    void seedIsIdempotentAndProducesExpectedCatalog() {
        JdbcTemplate jdbc = initializeDatabase("goodshort_seed");

        executeSeed(jdbc);
        Map<String, Long> dramaIdsBefore = jdbc.queryForList(
                        "SELECT external_drama_id, id FROM provider_drama "
                                + "WHERE external_drama_id LIKE '990000%' ORDER BY external_drama_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_DRAMA_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        Map<String, Long> contentIdsBefore = jdbc.queryForList(
                        "SELECT c.external_content_id, c.id FROM provider_drama_content c "
                                + "JOIN provider_drama d ON d.id = c.drama_id "
                                + "WHERE d.external_drama_id LIKE '990000%' ORDER BY c.external_content_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_CONTENT_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        Long connectionIdBefore = jdbc.queryForObject("SELECT id FROM short_drama_connection", Long.class);
        Map<String, Long> checkpointIdsBefore = jdbc.queryForList(
                        "SELECT sync_type, language, id FROM provider_sync_checkpoint")
                .stream()
                .collect(Collectors.toMap(row -> row.get("SYNC_TYPE") + ":" + row.get("LANGUAGE"),
                        row -> ((Number) row.get("ID")).longValue()));

        Long connectionId = connectionIdBefore;
        jdbc.update("INSERT INTO provider_drama (connection_id, external_drama_id, title, language, local_status) "
                        + "VALUES (?, '88000001', 'Out of range drama', 'ENGLISH', 'DRAFT')", connectionId);
        Long extraDramaId = jdbc.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id = '88000001'", Long.class);

        executeSeed(jdbc);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM short_drama_connection", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT id FROM short_drama_connection", Long.class))
                .isEqualTo(connectionIdBefore);
        Map<String, Object> connection = jdbc.queryForMap(
                "SELECT connection_name, currency, status, filing_mode, partner_id, api_key_ciphertext, base_url, media_root_domain "
                        + "FROM short_drama_connection");
        assertThat(connection.get("CONNECTION_NAME")).isEqualTo("GoodShort local fixture");
        assertThat(connection.get("CURRENCY")).isEqualTo("USD");
        assertThat(((Number) connection.get("STATUS")).intValue()).isZero();
        assertThat(connection.get("FILING_MODE")).isEqualTo("MANUAL");
        assertThat(connection.get("PARTNER_ID")).isNull();
        assertThat(connection.get("API_KEY_CIPHERTEXT")).isNull();
        assertThat(connection.get("BASE_URL")).isNull();
        assertThat(connection.get("MEDIA_ROOT_DOMAIN")).isNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class)).isEqualTo(25L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE external_drama_id LIKE '990000%'", Long.class))
                .isEqualTo(24L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama_content", Long.class)).isEqualTo(204L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_sync_checkpoint", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_sync_checkpoint WHERE status = 'SUCCESS'", Long.class)).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_sync_checkpoint WHERE last_error_code IS NULL "
                        + "AND last_error_message IS NULL AND lease_owner IS NULL AND lease_until IS NULL", Long.class))
                .isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'ENGLISH'", Long.class)).isEqualTo(13L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'SPANISH'", Long.class)).isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT local_status) FROM provider_drama", Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForList("SELECT local_status FROM provider_drama GROUP BY local_status", String.class))
                .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "OFFLINE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE cover_url IS NULL", Long.class)).isGreaterThan(0L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE cover_url IS NOT NULL "
                        + "AND cover_url NOT LIKE 'https://placehold.co/%'", Long.class)).isZero();
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
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama_content WHERE drama_id = ?", Long.class, extraDramaId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama_content c JOIN provider_drama d ON d.id = c.drama_id "
                        + "WHERE d.external_drama_id LIKE '990000%'", Long.class)).isEqualTo(204L);
        List<Map<String, Object>> checkpoints = jdbc.queryForList(
                "SELECT sync_type, language, total_fetched, inserted_count, updated_count, error_count "
                        + "FROM provider_sync_checkpoint ORDER BY sync_type, language");
        assertThat(checkpoints).containsExactlyInAnyOrder(
                Map.of("SYNC_TYPE", "FULL", "LANGUAGE", "ENGLISH", "TOTAL_FETCHED", 12,
                        "INSERTED_COUNT", 12, "UPDATED_COUNT", 0, "ERROR_COUNT", 0),
                Map.of("SYNC_TYPE", "FULL", "LANGUAGE", "SPANISH", "TOTAL_FETCHED", 12,
                        "INSERTED_COUNT", 12, "UPDATED_COUNT", 0, "ERROR_COUNT", 0),
                Map.of("SYNC_TYPE", "INCREMENTAL", "LANGUAGE", "ENGLISH", "TOTAL_FETCHED", 12,
                        "INSERTED_COUNT", 2, "UPDATED_COUNT", 9, "ERROR_COUNT", 0),
                Map.of("SYNC_TYPE", "INCREMENTAL", "LANGUAGE", "SPANISH", "TOTAL_FETCHED", 12,
                        "INSERTED_COUNT", 1, "UPDATED_COUNT", 10, "ERROR_COUNT", 0));
        List<Integer> episodeCounts = jdbc.queryForList(
                "SELECT COUNT(*) FROM provider_drama_content GROUP BY drama_id ORDER BY drama_id", Integer.class);
        assertThat(episodeCounts).hasSize(24);
        assertThat(episodeCounts).containsExactly(
                5, 6, 7, 8, 9, 10, 11, 12,
                5, 6, 7, 8, 9, 10, 11, 12,
                5, 6, 7, 8, 9, 10, 11, 12);

        Map<String, Long> dramaIdsAfter = jdbc.queryForList(
                        "SELECT external_drama_id, id FROM provider_drama "
                                + "WHERE external_drama_id LIKE '990000%' ORDER BY external_drama_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_DRAMA_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        Map<String, Long> contentIdsAfter = jdbc.queryForList(
                        "SELECT c.external_content_id, c.id FROM provider_drama_content c "
                                + "JOIN provider_drama d ON d.id = c.drama_id "
                                + "WHERE d.external_drama_id LIKE '990000%' ORDER BY c.external_content_id")
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("EXTERNAL_CONTENT_ID"),
                        row -> ((Number) row.get("ID")).longValue()));
        assertThat(dramaIdsAfter).isEqualTo(dramaIdsBefore);
        assertThat(contentIdsAfter).isEqualTo(contentIdsBefore);
        Map<String, Long> checkpointIdsAfter = jdbc.queryForList(
                        "SELECT sync_type, language, id FROM provider_sync_checkpoint")
                .stream()
                .collect(Collectors.toMap(row -> row.get("SYNC_TYPE") + ":" + row.get("LANGUAGE"),
                        row -> ((Number) row.get("ID")).longValue()));
        assertThat(checkpointIdsAfter).isEqualTo(checkpointIdsBefore);
    }

    @Test
    @DisplayName("存在真实 GoodShort 接入时本地种子拒绝写入")
    void seedRejectsExistingRealConnectionWithoutWritingFixtures() {
        JdbcTemplate jdbc = initializeDatabase("goodshort_seed");
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency, status, filing_mode, base_url) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                providerId, "GoodShort Real", "partner-live", "cipher-live", "USD", 1, "API", "https://api.goodshort.com");

        assertThatThrownBy(() -> executeSeed(jdbc)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class)).isZero();
        executeSeedContinueOnError(jdbc);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama_content", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_sync_checkpoint", Long.class)).isZero();
        Map<String, Object> connection = jdbc.queryForMap(
                "SELECT partner_id, status FROM short_drama_connection WHERE provider_id = ?", providerId);
        assertThat(connection.get("PARTNER_ID")).isEqualTo("partner-live");
        assertThat(((Number) connection.get("STATUS")).intValue()).isEqualTo(1);
    }

    private static void executeSeed(JdbcTemplate jdbc) {
        FileSystemResource resource = new FileSystemResource(SEED_SCRIPT);
        jdbc.execute((Connection connection) -> {
            try {
                ScriptUtils.executeSqlScript(connection, resource);
            } finally {
                dropTemporaryTablesForH2(connection);
            }
            return null;
        });
    }

    private static void executeSeedContinueOnError(JdbcTemplate jdbc) {
        FileSystemResource resource = new FileSystemResource(SEED_SCRIPT);
        EncodedResource encodedResource = new EncodedResource(resource);
        jdbc.execute((Connection connection) -> {
            try {
                ScriptUtils.executeSqlScript(connection, encodedResource, true, false,
                        ScriptUtils.DEFAULT_STATEMENT_SEPARATOR, ScriptUtils.DEFAULT_COMMENT_PREFIX,
                        ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                        ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER);
            } finally {
                dropTemporaryTablesForH2(connection);
            }
            return null;
        });
    }

    private static void dropTemporaryTablesForH2(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            for (String table : parseTemporaryTableNames(
                    Files.readString(Path.of(SEED_SCRIPT), StandardCharsets.UTF_8))) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException | java.io.IOException ex) {
            throw new IllegalStateException("Failed to clean H2 seed temporary tables", ex);
        }
    }

    private static Set<String> parseTemporaryTableNames(String script) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?i)CREATE\\s+TEMPORARY\\s+TABLE\\s+`?([a-z0-9_]+)`?")
                .matcher(script);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static Set<String> findMatches(String script, Pattern pattern) {
        Set<String> matches = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(script);
        while (matcher.find()) {
            matches.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return matches;
    }

    private static int countIdentifierReferences(String value, String identifier) {
        int count = 0;
        Matcher matcher = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(identifier)
                        + "(?![a-z0-9_])")
                .matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
