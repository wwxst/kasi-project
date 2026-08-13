package com.kasi.backend.user;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.vo.CurrentUserVO;
import com.kasi.backend.user.vo.UserLoginVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("推广用户字段结构")
class PromotionUserStructureTest extends BaseAuthTest {

    @Test
    @DisplayName("实体和认证响应不再暴露独立username")
    void promotionUserModelsDoNotExposeUsername() {
        assertThat(fieldNames(PromotionUser.class)).doesNotContain("username");
        assertThat(fieldNames(CurrentUserVO.class)).doesNotContain("username");
        assertThat(fieldNames(UserLoginVO.UserInfo.class)).doesNotContain("username");
    }

    @Test
    @DisplayName("推广用户数据库和Mapper不再声明username")
    void promotionUserSchemaAndMapperDoNotDeclareUsername() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V1__kasi_promotion.sql"), StandardCharsets.UTF_8);
        String promotionBlock = migration.substring(migration.indexOf("-- 推广用户表"));
        String testSchema = Files.readString(
                Path.of("src/test/resources/test-schema.sql"), StandardCharsets.UTF_8);
        String testPromotionBlock = testSchema.substring(testSchema.indexOf("CREATE TABLE IF NOT EXISTS promotion_user"));
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/PromotionUserMapper.xml"), StandardCharsets.UTF_8);

        assertThat(promotionBlock).doesNotContain("username");
        assertThat(testPromotionBlock).doesNotContain("username");
        assertThat(mapper).doesNotContain("username");
    }

    private static java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
