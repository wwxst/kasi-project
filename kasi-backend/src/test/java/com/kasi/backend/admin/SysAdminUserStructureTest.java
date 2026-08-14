package com.kasi.backend.admin;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.admin.entity.SysAdminUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("管理员字段结构")
class SysAdminUserStructureTest extends BaseAuthTest {

    @Test
    @DisplayName("管理员实体不保留软删除字段")
    void sysAdminUserEntityDoesNotDeclareSoftDeleteField() {
        assertThat(fieldNames(SysAdminUser.class)).doesNotContain("deletedAt");
    }

    @Test
    @DisplayName("管理员数据库和Mapper不再声明软删除字段")
    void sysAdminUserSchemaAndMapperDoNotDeclareSoftDeleteField() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V1__kasi_promotion.sql"), StandardCharsets.UTF_8);
        String adminBlock = migration.substring(
                migration.indexOf("CREATE TABLE `sys_admin_user`"), migration.indexOf("-- 推广用户表"));
        String testSchema = Files.readString(
                Path.of("src/test/resources/test-schema.sql"), StandardCharsets.UTF_8);
        String testAdminBlock = testSchema.substring(
                testSchema.indexOf("CREATE TABLE IF NOT EXISTS sys_admin_user"),
                testSchema.indexOf("CREATE TABLE IF NOT EXISTS promotion_user"));
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/SysAdminUserMapper.xml"), StandardCharsets.UTF_8);

        assertThat(adminBlock).doesNotContain("deleted_at");
        assertThat(testAdminBlock).doesNotContain("deleted_at");
        assertThat(mapper).doesNotContain("deleted_at");
    }

    private static java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
