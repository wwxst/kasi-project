package com.kasi.backend.architecture;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("历史兼容残留结构")
class HistoricalCompatibilityStructureTest extends BaseAuthTest {

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

    private static java.util.List<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }
}
