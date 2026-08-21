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
        assertThat(stored.getIntervalMinutes()).isEqualTo(30);
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getNextRunAt()).isEqualToIgnoringNanos(nextRunAt);
    }

    @Test
    @DisplayName("同一个到期任务只能被一个实例领取")
    void dueTaskHasSingleLeaseOwner() {
        SystemScheduledTask task = mapper.findByTaskCode(
                ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbcTemplate.update("UPDATE system_scheduled_task SET next_run_at = ? WHERE id = ?",
                now.minusMinutes(1), task.getId());

        assertThat(mapper.findDue(now, 10)).extracting(SystemScheduledTask::getId)
                .containsExactly(task.getId());
        assertThat(mapper.claimLease(task.getId(), "worker-a", now, now.plusMinutes(2)))
                .isEqualTo(1);
        assertThat(mapper.claimLease(task.getId(), "worker-b", now, now.plusMinutes(2)))
                .isZero();
        assertThat(mapper.completeRun(task.getId(), "worker-a", now.plusMinutes(60)))
                .isEqualTo(1);

        SystemScheduledTask completed = mapper.findByTaskCode(task.getTaskCode());
        assertThat(completed.getLeaseOwner()).isNull();
        assertThat(completed.getLeaseUntil()).isNull();
        assertThat(completed.getNextRunAt()).isEqualToIgnoringNanos(now.plusMinutes(60));
    }
}
