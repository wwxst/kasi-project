package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DramaAvailabilityMigrationTest {

    @Test
    @DisplayName("V18 将历史草稿按甲方状态转换并把新短剧默认设为已上架")
    void migrationConvertsDraftsAndChangesDefault() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("17").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long connectionId = seedConnection(jdbc);
        insertDrama(jdbc, connectionId, "online-draft", "1", "DRAFT");
        insertDrama(jdbc, connectionId, "offline-draft", "0", "DRAFT");
        insertDrama(jdbc, connectionId, "manual-offline", "1", "OFFLINE");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(status(jdbc, "online-draft")).isEqualTo("PUBLISHED");
        assertThat(status(jdbc, "offline-draft")).isEqualTo("OFFLINE");
        assertThat(status(jdbc, "manual-offline")).isEqualTo("OFFLINE");

        jdbc.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language,remote_show_status) VALUES (?,?,?,?,?)",
                connectionId, "new-online", "New online", "ENGLISH", "1");
        assertThat(status(jdbc, "new-online")).isEqualTo("PUBLISHED");
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:drama_availability_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
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
