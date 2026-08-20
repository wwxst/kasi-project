package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSuperAdminMigrationTest {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "kasi123456";

    @Test
    @DisplayName("V1迁移植入可登录的默认超级管理员")
    void migrateV1SeedsDefaultSuperAdmin() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:default_admin_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<Map<String, Object>> admins = jdbcTemplate.queryForList(
                "SELECT username, password, real_name, status, is_super_admin FROM sys_admin_user");

        assertThat(admins).singleElement().satisfies(admin -> {
            assertThat(admin.get("username")).isEqualTo(DEFAULT_USERNAME);
            assertThat(admin.get("real_name")).isEqualTo("系统管理员");
            assertThat(((Number) admin.get("status")).intValue()).isEqualTo(1);
            assertThat(((Number) admin.get("is_super_admin")).intValue()).isEqualTo(1);

            String storedPassword = (String) admin.get("password");
            assertThat(storedPassword).isNotEqualTo(DEFAULT_PASSWORD);
            assertThat(new BCryptPasswordEncoder().matches(DEFAULT_PASSWORD, storedPassword)).isTrue();
        });
    }
}
