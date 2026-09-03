package com.kasi.backend;

import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.support.MySqlContractTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MySQL 8.4生产契约")
@EnabledIfEnvironmentVariable(
        named = "MYSQL_CONTRACT_URL",
        matches = ".+",
        disabledReason = "SKIP: MYSQL_CONTRACT_URL is not configured")
class MySqlContractIT extends MySqlContractTestSupport {

    @Autowired
    private SystemScheduledTaskMapper scheduledTaskMapper;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("生产Schema没有物理外键且关键唯一索引真实存在")
    void schemaHasNoForeignKeysAndHasCriticalUniqueIndexes() {
        Long foreignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                """, Long.class);
        assertThat(foreignKeyCount).isZero();
        Long foreignKeyConstraintCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """, Long.class);
        assertThat(foreignKeyConstraintCount).isZero();

        assertUniqueIndex("short_drama_connection", "uk_drama_connection_provider", "provider_id");
        assertUniqueIndex("provider_drama", "uk_provider_drama_external",
                "connection_id", "external_drama_id");
        assertUniqueIndex("provider_drama_content", "uk_provider_drama_content_sequence",
                "drama_id", "sequence_no");
        assertUniqueIndex("provider_commission_rule", "uk_provider_commission_provider", "provider_id");
        assertUniqueIndex("promotion_link", "uk_promotion_link_variant",
                "user_id", "request_key", "media_type", "link_variant");
        assertUniqueIndex("promotion_order", "uk_promotion_order_source",
                "connection_id", "external_order_id");
    }

    @Test
    @DisplayName("金额与费率DECIMAL元数据和往返精度保持不变")
    void decimalMetadataAndRoundTripRemainExact() {
        for (String column : List.of(
                "channel_fee_rate", "principal_fee_rate", "principal_commission_rate",
                "downstream_fee_rate", "downstream_commission_rate")) {
            assertDecimal("provider_commission_rule", column, 12, 10);
            assertDecimal("promotion_order", column, 12, 10);
        }
        assertDecimal("promotion_order", "order_amount", 18, 2);
        assertDecimal("promotion_order", "commission_amount", 18, 2);

        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        BigDecimal rate = new BigDecimal("0.1234567891");
        jdbcTemplate.update("""
                INSERT INTO provider_commission_rule
                    (provider_id, channel_fee_rate, principal_fee_rate, principal_commission_rate,
                     downstream_fee_rate, downstream_commission_rate, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 1, 1)
                """, providerId, rate, rate, rate, rate, rate);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT downstream_commission_rate
                FROM provider_commission_rule
                WHERE provider_id = ?
                """, BigDecimal.class, providerId)).isEqualByComparingTo(rate);

        BigDecimal orderAmount = new BigDecimal("1234567890123456.78");
        BigDecimal commissionAmount = new BigDecimal("9876543210987654.32");
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        jdbcTemplate.update("""
                INSERT INTO promotion_order
                    (connection_id, provider_id, external_order_id, order_amount_minor, order_amount,
                     currency, raw_status, status, channel_fee_rate, principal_fee_rate,
                     principal_commission_rate, downstream_fee_rate, downstream_commission_rate,
                     commission_amount, raw_payload_json, sync_start_date, sync_end_date, last_synced_at)
                VALUES (1, ?, 'mysql-decimal-contract', 1, ?, 'USD', 'PAID', 'PAID',
                        ?, ?, ?, ?, ?, ?, '{}', ?, ?, ?)
                """, providerId, orderAmount, rate, rate, rate, rate, rate, commissionAmount,
                now.minusDays(1), now, now);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT order_amount
                FROM promotion_order
                WHERE external_order_id = 'mysql-decimal-contract'
                """, BigDecimal.class)).isEqualByComparingTo(orderAmount);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT commission_amount
                FROM promotion_order
                WHERE external_order_id = 'mysql-decimal-contract'
                """, BigDecimal.class)).isEqualByComparingTo(commissionAmount);
    }

    @Test
    @DisplayName("Java与MySQL使用同一任务时间并完成到期租约流程")
    void scheduledTaskSqlUsesTheSameBusinessTimeAsJava() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class))
                .isEqualTo("+08:00");

        LocalDateTime mysqlNow = jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP(6)", Timestamp.class).toLocalDateTime();
        LocalDateTime javaNow = LocalDateTime.now(clock);
        assertThat(Duration.between(mysqlNow, javaNow).abs()).isLessThan(Duration.ofSeconds(5));

        ScheduledTaskCode taskCode = ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC;
        jdbcTemplate.update("""
                UPDATE system_scheduled_task
                SET next_run_at = ?, lease_owner = NULL, lease_until = NULL, enabled = 1
                WHERE task_code = ?
                """, mysqlNow.minusSeconds(1), taskCode.name());

        assertThat(scheduledTaskMapper.findDue(javaNow, 10))
                .extracting(SystemScheduledTask::getTaskCode)
                .contains(taskCode);
        assertThat(scheduledTaskMapper.claimLease(taskCode, "mysql-worker-a",
                javaNow, javaNow.plusMinutes(2))).isEqualTo(1);
        assertThat(scheduledTaskMapper.claimLease(taskCode, "mysql-worker-b",
                javaNow, javaNow.plusMinutes(2))).isZero();

        LocalDateTime nextRunAt = javaNow.plusMinutes(60).withNano(0);
        assertThat(scheduledTaskMapper.completeRun(taskCode, "mysql-worker-a", nextRunAt))
                .isEqualTo(1);
        SystemScheduledTask completed = scheduledTaskMapper.findByTaskCode(taskCode);
        assertThat(completed.getLeaseOwner()).isNull();
        assertThat(completed.getLeaseUntil()).isNull();
        assertThat(completed.getNextRunAt()).isEqualToIgnoringNanos(nextRunAt);
        assertThat(scheduledTaskMapper.findDue(javaNow, 10))
                .extracting(SystemScheduledTask::getTaskCode)
                .doesNotContain(taskCode);
    }

    private void assertUniqueIndex(String tableName, String indexName, String... expectedColumns) {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                  AND NON_UNIQUE = 0
                ORDER BY SEQ_IN_INDEX
                """, String.class, tableName, indexName);
        assertThat(columns).containsExactly(expectedColumns);
    }

    private void assertDecimal(String tableName, String columnName, int precision, int scale) {
        DecimalMetadata metadata = jdbcTemplate.queryForObject("""
                SELECT NUMERIC_PRECISION, NUMERIC_SCALE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, (resultSet, rowNum) -> new DecimalMetadata(
                        resultSet.getInt("NUMERIC_PRECISION"), resultSet.getInt("NUMERIC_SCALE")),
                tableName, columnName);
        assertThat(metadata).isEqualTo(new DecimalMetadata(precision, scale));
    }

    private record DecimalMetadata(int precision, int scale) {
    }
}
