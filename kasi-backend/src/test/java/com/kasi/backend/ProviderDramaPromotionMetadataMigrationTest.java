package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class ProviderDramaPromotionMetadataMigrationTest {

    @Test
    @DisplayName("初始化脚本定义短剧目录推广元数据字段")
    void initializationDefinesPromotionMetadataColumns() {
        JdbcTemplate jdbc = initializeDatabase("promotion_metadata");

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
