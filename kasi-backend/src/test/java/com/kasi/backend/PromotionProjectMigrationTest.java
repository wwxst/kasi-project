package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static com.kasi.backend.support.DatabaseInitializationTestSupport.initializeDatabase;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class PromotionProjectMigrationTest {

    @Test
    @DisplayName("V6迁移和开发重建脚本定义相同的推广项目核心字段")
    void migrationAndInitializationDefinePromotionProject() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V6__promotion_project.sql");
        assertThat(migration.exists()).isTrue();

        String migrationSql = migration.getContentAsString(StandardCharsets.UTF_8);
        String initializationSql = new ClassPathResource("db/kasi_promotion.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertPromotionProjectShape(migrationSql);
        assertPromotionProjectShape(initializationSql);
    }

    @Test
    @DisplayName("推广项目默认启用并支持稳定排序和物理删除")
    void initializationSupportsProjectDefaultsOrderingAndPhysicalDelete() {
        JdbcTemplate jdbc = initializeDatabase("promotion_project");

        jdbc.update("INSERT INTO promotion_project "
                        + "(name, cover_image_url, project_document_url, sort_order) VALUES (?,?,?,?)",
                "项目B", "/uploads/promotion-projects/b.webp", "https://example.com/b", 20);
        jdbc.update("INSERT INTO promotion_project "
                        + "(name, cover_image_url, project_document_url, sort_order) VALUES (?,?,?,?)",
                "项目A", "/uploads/promotion-projects/a.webp", "https://example.com/a", 10);

        assertThat(jdbc.queryForList(
                "SELECT name FROM promotion_project WHERE status='ENABLED' ORDER BY sort_order ASC, id ASC",
                String.class)).containsExactly("项目A", "项目B");

        jdbc.update("DELETE FROM promotion_project WHERE name='项目A'");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM promotion_project WHERE name='项目A'", Integer.class)).isZero();
    }

    private static void assertPromotionProjectShape(String sql) {
        assertThat(sql)
                .contains("promotion_project")
                .contains("name")
                .contains("cover_image_url")
                .contains("project_document_url")
                .contains("status")
                .contains("sort_order")
                .contains("created_at")
                .contains("updated_at")
                .contains("idx_promotion_project_user_list");
    }
}
