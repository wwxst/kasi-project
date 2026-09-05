package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class PromotionAnalyticalReportMigrationTest {
    @Test
    @DisplayName("初始化脚本创建转化日报表及自然日维度唯一键")
    void initializationCreatesAnalyticalReportTable() {
        JdbcTemplate jdbc = initializeDatabase("promotion_analytical_report");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME='PROMOTION_ANALYTICAL_REPORT'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                + "WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME='PROMOTION_ANALYTICAL_REPORT' "
                + "AND COLUMN_NAME IN ('REPORT_DATE','PID','CUSTOM_PARAMS','BOOK_ID','CODE')", Integer.class))
                .isGreaterThanOrEqualTo(5);
    }
}
