package com.kasi.backend.scheduledtask;

import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskLocalizationTest {

    @Test
    @DisplayName("GoodShort 转化日报任务使用中文标题")
    void analyticalReportTaskUsesChineseTitle() {
        assertThat(ScheduledTaskCode.GOODSHORT_ANALYTICAL_REPORT_SYNC.title())
                .isEqualTo("GoodShort 转化日报同步");
    }

    @Test
    @DisplayName("存量迁移与空库初始化使用相同的中文任务说明")
    void analyticalReportTaskUsesChineseDescription() throws Exception {
        String expectedDescription = "每天 08:00 同步 GoodShort 推广转化日报";
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V10__localize_goodshort_analytical_report_task.sql");
        ClassPathResource initialization = new ClassPathResource("db/kasi_promotion.sql");

        assertThat(migration.exists()).isTrue();
        assertThat(migration.getContentAsString(StandardCharsets.UTF_8))
                .contains("GOODSHORT_ANALYTICAL_REPORT_SYNC", expectedDescription,
                        "Daily 08:00 GoodShort analytical report sync");
        assertThat(initialization.getContentAsString(StandardCharsets.UTF_8))
                .contains("GOODSHORT_ANALYTICAL_REPORT_SYNC", expectedDescription);
    }
}
