package com.kasi.backend.scheduledtask.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("系统定时任务持久化")
class SystemScheduledTaskPersistenceTest extends BaseAuthTest {

    @Autowired
    private SystemScheduledTaskMapper mapper;

    @Test
    @DisplayName("定时任务配置可查询并更新")
    void taskCanBeReadAndUpdated() {
        SystemScheduledTask task = mapper.findByTaskCode(
                ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);
        LocalDateTime nextRunAt = LocalDateTime.now().plusMinutes(30).withNano(0);

        assertThat(task).isNotNull();
        assertThat(mapper.updateConfig(task.getTaskCode(), "更新后的说明", 30, true, nextRunAt))
                .isEqualTo(1);

        SystemScheduledTask stored = mapper.findByTaskCode(task.getTaskCode());
        assertThat(stored.getDescription()).isEqualTo("更新后的说明");
        assertThat(stored.getIntervalValue()).isEqualTo(30);
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getNextRunAt()).isEqualToIgnoringNanos(nextRunAt);
    }

    @Test
    @DisplayName("大于一天的结构化周期仍兼容旧分钟字段约束")
    void structuredDayCycleClampsLegacyIntervalMinutes() {
        SystemScheduledTask task = mapper.findByTaskCode(
                ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);

        assertThat(mapper.updateConfig(task.getTaskCode(), "每天执行", "INTERVAL_DAYS", 3,
                null, null, null, null, true, LocalDateTime.now().plusDays(3)))
                .isEqualTo(1);

        SystemScheduledTask stored = mapper.findByTaskCode(task.getTaskCode());
        assertThat(stored.getCycleType().name()).isEqualTo("INTERVAL_DAYS");
        assertThat(stored.getIntervalValue()).isEqualTo(3);
        assertThat(stored.getIntervalValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("同一个到期任务只能被一个实例领取")
    void dueTaskHasSingleLeaseOwner() {
        SystemScheduledTask task = mapper.findByTaskCode(
                ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update("UPDATE system_scheduled_task SET next_run_at = ? WHERE task_code = ?",
                now.minusMinutes(1), task.getTaskCode().name());

        assertThat(mapper.findDue(now, 10)).extracting(SystemScheduledTask::getTaskCode)
                .containsExactly(task.getTaskCode());
        assertThat(mapper.claimLease(task.getTaskCode().name(), "worker-a", now, now.plusMinutes(2)))
                .isEqualTo(1);
        assertThat(mapper.claimLease(task.getTaskCode().name(), "worker-b", now, now.plusMinutes(2)))
                .isZero();
        assertThat(mapper.completeRun(task.getTaskCode().name(), "worker-a", now.plusMinutes(60)))
                .isEqualTo(1);

        SystemScheduledTask completed = mapper.findByTaskCode(task.getTaskCode());
        assertThat(completed.getLeaseOwner()).isNull();
        assertThat(completed.getLeaseUntil()).isNull();
        assertThat(completed.getNextRunAt()).isEqualToIgnoringNanos(now.plusMinutes(60));
    }
}
