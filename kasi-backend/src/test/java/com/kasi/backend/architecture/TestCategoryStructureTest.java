package com.kasi.backend.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("后端测试分类结构")
class TestCategoryStructureTest {

    private static final List<String> INTEGRATION_TESTS = List.of(
            "com.kasi.backend.BaseAuthTest",
            "com.kasi.backend.DefaultSuperAdminMigrationTest",
            "com.kasi.backend.DramaAvailabilityMigrationTest",
            "com.kasi.backend.KasiBackendApplicationTests",
            "com.kasi.backend.MediaAccountFilingMigrationTest",
            "com.kasi.backend.PromotionLinkMigrationTest",
            "com.kasi.backend.PromotionOrderMigrationTest",
            "com.kasi.backend.PromotionUserMigrationTest",
            "com.kasi.backend.ProviderCommissionRuleMigrationTest",
            "com.kasi.backend.ProviderDramaPromotionMetadataMigrationTest",
            "com.kasi.backend.ScheduledTaskMigrationTest",
            "com.kasi.backend.auth.service.PasswordResetTokenServiceRedisTest",
            "com.kasi.backend.drama.GoodShortDramaCatalogSeedTest"
    );

    @Test
    @DisplayName("Spring、数据库和Redis测试归入集成测试")
    void springDatabaseAndRedisTestsAreIntegrationTests() {
        assertThat(INTEGRATION_TESTS)
                .allSatisfy(className -> assertThat(tagsOf(className)).contains("integration"));
    }

    @Test
    @DisplayName("真实MySQL与GoodShort测试使用独立分类")
    void externalContractsUseDedicatedCategories() {
        assertDedicatedCategory("com.kasi.backend.MySqlContractIT", "mysql-contract");
        assertDedicatedCategory("com.kasi.backend.promotion.PromotionOrderMySqlContractIT", "mysql-contract");
        assertDedicatedCategory("com.kasi.backend.promotion.LockingMySqlContractIT", "mysql-contract");
        assertDedicatedCategory("com.kasi.backend.integration.TransactionMySqlContractIT", "mysql-contract");
        assertThat(tagsOf("com.kasi.backend.provider.goodshort.GoodShortFreeContentIntegrationTest"))
                .contains("real-smoke")
                .doesNotContain("integration");
    }

    private void assertDedicatedCategory(String className, String category) {
        assertThat(tagsOf(className)).contains(category).doesNotContain("integration");
    }

    private Set<String> tagsOf(String className) {
        try {
            return AnnotationSupport.findRepeatableAnnotations(Class.forName(className), Tag.class).stream()
                    .map(Tag::value)
                    .collect(Collectors.toSet());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("测试类不存在: " + className, exception);
        }
    }
}
