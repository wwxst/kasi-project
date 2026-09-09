package com.kasi.backend.user;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
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
    @DisplayName("实体不保留用户名或软删除字段且认证响应不暴露用户名")
    void promotionUserModelsDoNotExposeRemovedFields() {
        assertThat(fieldNames(PromotionUser.class)).doesNotContain("username", "deletedAt");
        assertThat(fieldNames(PromotionUser.class)).contains("studentType");
        assertThat(fieldNames(CurrentUserVO.class)).doesNotContain("id", "username");
        assertThat(fieldNames(CurrentUserVO.class)).contains("studentType");
        assertThat(fieldNames(UserLoginVO.UserInfo.class)).doesNotContain("id", "username");
    }

    @Test
    @DisplayName("推广用户数据库和Mapper不再声明用户名或软删除字段")
    void promotionUserSchemaAndMapperDoNotDeclareRemovedFields() throws Exception {
        String initialization = Files.readString(
                Path.of("src/main/resources/db/kasi_promotion.sql"), StandardCharsets.UTF_8);
        String promotionBlock = initialization.substring(
                initialization.indexOf("-- 推广用户表"), initialization.indexOf("-- 阿里云短信当前配置"));
        String testSchema = Files.readString(
                Path.of("src/test/resources/test-schema.sql"), StandardCharsets.UTF_8);
        String testPromotionBlock = testSchema.substring(
                testSchema.indexOf("CREATE TABLE IF NOT EXISTS promotion_user"),
                testSchema.indexOf("CREATE TABLE IF NOT EXISTS system_sms_config"));
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/PromotionUserMapper.xml"), StandardCharsets.UTF_8);

        assertThat(promotionBlock).doesNotContain("username", "deleted_at");
        assertThat(testPromotionBlock).doesNotContain("username", "deleted_at");
        assertThat(mapper).doesNotContain("username", "deleted_at");
        assertThat(promotionBlock)
                .contains("`user_no`         CHAR(12)", "`wechat_id`       VARCHAR(128)",
                        "`student_type`    TINYINT         NOT NULL DEFAULT 0",
                        "UNIQUE KEY `uk_user_no` (`user_no`)");
        assertThat(testPromotionBlock)
                .contains("user_no CHAR(12) NOT NULL", "wechat_id VARCHAR(128)",
                        "student_type TINYINT NOT NULL DEFAULT 0", "UNIQUE (user_no)");
        assertThat(Files.readString(
                Path.of("src/main/resources/db/migration/V7__user_profile_contacts.sql"), StandardCharsets.UTF_8))
                .contains("ADD COLUMN `wechat_id`");
        assertThat(Files.readString(
                Path.of("src/main/resources/db/migration/V8__user_student_type.sql"), StandardCharsets.UTF_8))
                .contains("ADD COLUMN `student_type`");
        assertThat(Arrays.stream(PromotionUserMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("updateUserNo");
        assertThat(mapper).doesNotContain("updateUserNo");
    }

    private static java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
