package com.kasi.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DisplayName("短信配置初始化")
class SmsConfigMigrationTest {

    @Test
    @DisplayName("初始化脚本创建单例短信配置表且不植入凭据")
    void initializationCreatesEmptySingletonConfig() {
        JdbcTemplate jdbc = initializeDatabase();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = 'SYSTEM_SMS_CONFIG'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_sms_config", Integer.class))
                .isZero();

        jdbc.update("""
                INSERT INTO system_sms_config
                    (id, access_key_id_ciphertext, access_key_secret_ciphertext,
                     sign_name, register_template_code, login_template_code,
                     reset_password_template_code, enabled, created_by, updated_by)
                VALUES (1, 'cipher-id', 'cipher-secret', '卡司',
                        'SMS_100', 'SMS_101', 'SMS_102', 0, 1, 1)
                """);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_sms_config", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO system_sms_config
                    (id, access_key_id_ciphertext, access_key_secret_ciphertext,
                     sign_name, register_template_code, login_template_code,
                     reset_password_template_code, enabled, created_by, updated_by)
                VALUES (2, 'cipher-id', 'cipher-secret', '卡司',
                        'SMS_100', 'SMS_101', 'SMS_102', 0, 1, 1)
                """))
                .isInstanceOf(RuntimeException.class);
    }

    private JdbcTemplate initializeDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:sms_config_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource("db/kasi_promotion.sql"));
        populator.execute(dataSource);
        return new JdbcTemplate(dataSource);
    }
}
