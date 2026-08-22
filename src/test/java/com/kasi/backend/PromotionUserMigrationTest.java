package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionUserMigrationTest {

    private static final String INITIAL_EMAIL = "19193171667@163.com";
    private static final String INITIAL_PASSWORD = "12345678";

    @Test
    @DisplayName("V1将推广用户编号定义为12位字符并保持唯一")
    void migrateV1DefinesFixedUniqueUserNumber() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:promotion_user_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Map<String, Object> seededUser = jdbcTemplate.queryForMap(
                "SELECT user_no, password, email, mobile, status FROM promotion_user WHERE email = ?",
                INITIAL_EMAIL);
        assertThat(seededUser.get("user_no").toString()).matches("[1-9][0-9]{11}");
        assertThat(seededUser.get("mobile")).isNull();
        assertThat(((Number) seededUser.get("status")).intValue()).isEqualTo(1);
        String storedPassword = (String) seededUser.get("password");
        assertThat(storedPassword).isNotEqualTo(INITIAL_PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(INITIAL_PASSWORD, storedPassword)).isTrue();

        Integer length = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'PROMOTION_USER' AND COLUMN_NAME = 'USER_NO'",
                Integer.class);
        assertThat(length).isEqualTo(12);

        jdbcTemplate.update("INSERT INTO promotion_user (user_no, password) VALUES (?, ?)",
                "100000000001", "hash");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO promotion_user (user_no, password) VALUES (?, ?)",
                "100000000001", "hash"))
                .isInstanceOf(DataAccessException.class);
    }
}
