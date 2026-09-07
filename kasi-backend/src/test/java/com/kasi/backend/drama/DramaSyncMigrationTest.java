package com.kasi.backend.drama;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("短剧目录同步数据库迁移")
class DramaSyncMigrationTest {

    @Test
    @DisplayName("V3将旧分页大小和游标修正为甲方上限")
    void v3NormalizesExistingCheckpointPageSize() {
        JdbcTemplate jdbc = initializeDatabase("drama_sync_migration");
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency) "
                        + "VALUES (?, 'GoodShort', 'partner-1', 'ciphertext', 'USD')",
                providerId);
        Long connectionId = jdbc.queryForObject(
                "SELECT id FROM short_drama_connection LIMIT 1", Long.class);
        jdbc.update("INSERT INTO provider_sync_checkpoint "
                        + "(connection_id, sync_type, language, status, page_no, page_size, total_fetched, inserted_count, updated_count) "
                        + "VALUES (?, 'FULL', 'ENGLISH', 'REQUESTED', 7, 100, 12, 8, 4)",
                connectionId);
        jdbc.update("INSERT INTO provider_sync_checkpoint "
                        + "(connection_id, sync_type, language, status, page_no, page_size, total_fetched, inserted_count, updated_count) "
                        + "VALUES (?, 'FULL', 'SPANISH', 'SUCCESS', 7, 100, 12, 8, 4)",
                connectionId);

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V3__normalize_drama_sync_page_size.sql")).execute(jdbc.getDataSource());

        assertThat(jdbc.queryForMap("SELECT page_no, page_size, total_fetched, inserted_count, updated_count "
                        + "FROM provider_sync_checkpoint WHERE language = 'ENGLISH'")).containsEntry("PAGE_NO", 1)
                .containsEntry("PAGE_SIZE", 50)
                .containsEntry("TOTAL_FETCHED", 0)
                .containsEntry("INSERTED_COUNT", 0)
                .containsEntry("UPDATED_COUNT", 0);
        assertThat(jdbc.queryForMap("SELECT page_no, page_size, total_fetched, inserted_count, updated_count "
                        + "FROM provider_sync_checkpoint WHERE language = 'SPANISH'")).containsEntry("PAGE_NO", 7)
                .containsEntry("PAGE_SIZE", 50)
                .containsEntry("TOTAL_FETCHED", 12)
                .containsEntry("INSERTED_COUNT", 8)
                .containsEntry("UPDATED_COUNT", 4);
    }
}
