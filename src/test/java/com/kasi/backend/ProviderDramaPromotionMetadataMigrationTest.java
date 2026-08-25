package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderDramaPromotionMetadataMigrationTest {

    @Test
    @DisplayName("V14为短剧目录增加可维护的推广元数据字段")
    void migrationAddsPromotionMetadataColumns() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:promotion_metadata_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROVIDER_DRAMA'
                  AND COLUMN_NAME IN ('COMMISSION_SCOPE', 'PROMOTION_DESCRIPTION')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'PROVIDER_DRAMA'
                  AND COLUMN_NAME IN ('TITLE_ZH', 'LABEL_NAMES', 'CATEGORY_NAME', 'REMOTE_RANK',
                                      'NOVEL_TYPE', 'NOVEL_SUB_TYPE', 'REMOTE_CREATED_AT')
                """, Integer.class)).isEqualTo(7);
    }
}
