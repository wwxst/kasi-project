package com.kasi.backend.architecture;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.download.entity.DramaDownloadTask;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("历史兼容残留结构")
class HistoricalCompatibilityStructureTest extends BaseAuthTest {

    private static final Pattern QUERY_INDEX_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:KEY|INDEX)\\s+`?(idx_[a-z0-9_]+)`?\\s*\\(([^)]+)\\)");

    @Test
    @DisplayName("Token服务只保留会话感知的生成和解析入口")
    void tokenServiceDoesNotExposeLegacyMethods() {
        assertThat(methodNames(TokenService.class))
                .containsExactlyInAnyOrder("generateToken", "parseToken");
        assertThat(Arrays.stream(TokenService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("generateToken"))
                .map(Method::getParameterCount))
                .containsExactly(5);
    }

    @Test
    @DisplayName("推广用户Mapper不保留无调用的编号查询")
    void promotionUserMapperDoesNotExposeUnusedUserNoLookup() throws Exception {
        String mapperXml = Files.readString(
                Path.of("src/main/resources/mapper/PromotionUserMapper.xml"), StandardCharsets.UTF_8);

        assertThat(methodNames(PromotionUserMapper.class)).doesNotContain("findByUserNo");
        assertThat(mapperXml).doesNotContain("findByUserNo");
    }

    @Test
    @DisplayName("错误码枚举不保留不可达错误码")
    void errorCodeDoesNotContainUnusedCompatibilityConstants() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
                .doesNotContain(
                        "SUCCESS",
                        "BAD_REQUEST",
                        "NOT_FOUND",
                        "USER_ACCOUNT_REQUIRED",
                        "VERIFICATION_CODE_EXPIRED",
                        "VERIFICATION_CODE_ALREADY_USED",
                        "RESET_TOKEN_EXPIRED",
                        "RESET_TOKEN_ALREADY_USED");
    }

    @Test
    @DisplayName("应用配置不再包含数据库自动迁移配置")
    void applicationDoesNotConfigureAutomaticDatabaseMigration() throws Exception {
        String applicationProperties = Files.readString(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8);

        assertThat(applicationProperties).doesNotContain("spring.flyway.");
    }

    @Test
    @DisplayName("认证测试基类不使用Jackson废弃文本访问器")
    void baseAuthTestDoesNotUseDeprecatedJsonNodeTextAccessor() throws Exception {
        String baseAuthTest = Files.readString(
                Path.of("src/test/java/com/kasi/backend/BaseAuthTest.java"), StandardCharsets.UTF_8);

        assertThat(baseAuthTest).doesNotContain(".asText()");
    }

    @Test
    @DisplayName("实体不暴露已从数据库删除的兼容字段")
    void entitiesDoNotExposeRemovedSchemaFields() {
        assertThat(fieldNames(PromotionLink.class))
                .doesNotContain("mediaAccountId", "providerCode", "mediaAccountName", "landingType");
        assertThat(fieldNames(PromotionOrder.class))
                .doesNotContain("firstSyncedAt", "mediaAccountId");
        assertThat(fieldNames(ProviderSyncCheckpoint.class))
                .doesNotContain("totalUpserted", "skippedCount");
        assertThat(fieldNames(ProviderMediaFiling.class, ProviderDramaContent.class,
                DramaDownloadTask.class, ShortDramaProvider.class))
                .doesNotContain("createdAt", "updatedAt");
    }

    @Test
    @DisplayName("测试 schema 与生产初始化 SQL 保持查询索引一致")
    void testSchemaContainsProductionQueryIndexes() throws Exception {
        String production = Files.readString(
                Path.of("src/main/resources/db/kasi_promotion.sql"), StandardCharsets.UTF_8);
        String testSchema = Files.readString(
                Path.of("src/test/resources/test-schema.sql"), StandardCharsets.UTF_8);

        Set<String> productionIndexes = queryIndexes(production);
        assertThat(productionIndexes).isNotEmpty();
        Set<String> testIndexes = queryIndexes(testSchema);
        assertThat(testIndexes).containsExactlyInAnyOrderElementsOf(productionIndexes);
    }

    private static Set<String> queryIndexes(String schema) {
        Matcher matcher = QUERY_INDEX_PATTERN.matcher(schema);
        Set<String> indexes = new LinkedHashSet<>();
        while (matcher.find()) {
            String columns = Arrays.stream(matcher.group(2).split(","))
                    .map(column -> column.replace("`", "")
                            .replaceAll("\\s+", "")
                            .toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(","));
            indexes.add(matcher.group(1).toLowerCase(Locale.ROOT) + " (" + columns + ")");
        }
        return indexes;
    }

    private static List<String> fieldNames(Class<?>... types) {
        return Arrays.stream(types)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toList());
    }

    private static java.util.List<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }
}
