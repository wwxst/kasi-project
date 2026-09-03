package com.kasi.backend.support;

import com.kasi.backend.auth.service.VerificationCodeSender;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/kasi_promotion.sql",
        "app.jwt.secret=mysql-contract-jwt-secret-must-be-at-least-256-bits-long",
        "app.promotion.filing.scheduler-enabled=false",
        "app.promotion.drama.sync.scheduler-enabled=false",
        "app.scheduled-task.scheduler-enabled=false"
})
@Tag("mysql-contract")
public abstract class MySqlContractTestSupport {

    protected static final String CONTRACT_PREFIX = "mysql-contract-";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    @MockitoBean
    protected VerificationCodeSender verificationCodeSender;

    @DynamicPropertySource
    static void mysqlDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requireEnvironment("MYSQL_CONTRACT_URL"));
        registry.add("spring.datasource.username", () -> requireEnvironment("MYSQL_CONTRACT_USERNAME"));
        registry.add("spring.datasource.password", () -> requireEnvironment("MYSQL_CONTRACT_PASSWORD"));
    }

    @BeforeEach
    void cleanContractFixtures() {
        Long providerId = goodShortProviderId();
        jdbcTemplate.update("DELETE FROM promotion_order WHERE external_order_id LIKE ?", CONTRACT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM promotion_link WHERE request_key LIKE ?", CONTRACT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM provider_commission_rule_history WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_commission_rule WHERE provider_id = ?", providerId);
        jdbcTemplate.update("DELETE FROM provider_drama WHERE external_drama_id LIKE ?", CONTRACT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM short_drama_connection WHERE connection_name LIKE ?", CONTRACT_PREFIX + "%");
        jdbcTemplate.update("""
                UPDATE system_scheduled_task
                SET lease_owner = NULL, lease_until = NULL
                WHERE lease_owner LIKE ?
                """, CONTRACT_PREFIX + "%");
    }

    protected Long goodShortProviderId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }

    protected Long primaryUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE email = '19193171667@163.com'", Long.class);
    }

    protected Long insertConnection(String suffix) {
        Long providerId = goodShortProviderId();
        String name = CONTRACT_PREFIX + suffix;
        jdbcTemplate.update("""
                INSERT INTO short_drama_connection
                    (provider_id, connection_name, partner_id, currency, filing_mode, status)
                VALUES (?, ?, 'mysql-contract-partner', 'USD', 'API', 1)
                """, providerId, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE connection_name = ?", Long.class, name);
    }

    protected Long insertDrama(Long connectionId, String suffix) {
        String externalDramaId = CONTRACT_PREFIX + suffix;
        jdbcTemplate.update("""
                INSERT INTO provider_drama
                    (connection_id, external_drama_id, title, language, remote_show_status, local_status)
                VALUES (?, ?, 'MySQL Contract Drama', 'ENGLISH', '1', 'PUBLISHED')
                """, connectionId, externalDramaId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id = ?", Long.class, externalDramaId);
    }

    protected Long insertPromotionLink(Long connectionId, Long dramaId, String suffix) {
        Long userId = primaryUserId();
        Long providerId = goodShortProviderId();
        String requestKey = CONTRACT_PREFIX + suffix;
        String trackingNo = CONTRACT_PREFIX + "tracking-" + suffix;
        jdbcTemplate.update("""
                INSERT INTO promotion_link
                    (user_id, provider_id, connection_id, drama_id, batch_no, media_type,
                     link_variant, request_key, tracking_no, status)
                VALUES (?, ?, ?, ?, ?, 'TIKTOK', 'LANDING', ?, ?, 'PENDING')
                """, userId, providerId, connectionId, dramaId,
                CONTRACT_PREFIX + "batch-" + suffix, requestKey, trackingNo);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_link WHERE request_key = ?", Long.class, requestKey);
    }

    protected String trackingNo(String suffix) {
        return CONTRACT_PREFIX + "tracking-" + suffix;
    }

    protected void insertCommissionHistory() {
        jdbcTemplate.update("""
                INSERT INTO provider_commission_rule_history
                    (provider_id, rule_id, channel_fee_rate, principal_fee_rate,
                     principal_commission_rate, downstream_fee_rate,
                     downstream_commission_rate, created_by)
                VALUES (?, 1, 0.0100000000, 0.0200000000, 0.5000000000,
                        0.0300000000, 0.8000000000, 1)
                """, goodShortProviderId());
    }

    protected ProviderRuntimeConnection runtime(Long connectionId) {
        Long providerId = goodShortProviderId();
        return new ProviderRuntimeConnection(connectionId, providerId, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("https://example.invalid", "partner", "secret", "USD"), null);
    }

    protected static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
