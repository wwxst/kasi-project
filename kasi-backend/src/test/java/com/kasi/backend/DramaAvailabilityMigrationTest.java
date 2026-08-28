package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

class DramaAvailabilityMigrationTest {

    @Test
    @DisplayName("初始化脚本把新短剧默认设为已上架并保留显式下架状态")
    void initializationDefinesPublishedDefaultAndAllowsOfflineStatus() {
        JdbcTemplate jdbc = initializeDatabase("drama_availability");
        Long connectionId = seedConnection(jdbc);
        insertDrama(jdbc, connectionId, "manual-offline", "1", "OFFLINE");

        assertThat(status(jdbc, "manual-offline")).isEqualTo("OFFLINE");

        jdbc.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language,remote_show_status) VALUES (?,?,?,?,?)",
                connectionId, "new-online", "New online", "ENGLISH", "1");
        assertThat(status(jdbc, "new-online")).isEqualTo("PUBLISHED");
    }

    private Long seedConnection(JdbcTemplate jdbc) {
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                + "(provider_id,connection_name,currency) VALUES (?,'GoodShort','USD')", providerId);
        return jdbc.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
    }

    private void insertDrama(JdbcTemplate jdbc, Long connectionId, String externalId,
                             String remoteStatus, String localStatus) {
        jdbc.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language,remote_show_status,local_status) "
                        + "VALUES (?,?,?,?,?,?)",
                connectionId, externalId, externalId, "ENGLISH", remoteStatus, localStatus);
    }

    private String status(JdbcTemplate jdbc, String externalId) {
        return jdbc.queryForObject(
                "SELECT local_status FROM provider_drama WHERE external_drama_id=?", String.class, externalId);
    }
}
