package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTaskScheduleCalculatorTest {

    private final ScheduledTaskScheduleCalculator calculator = new ScheduledTaskScheduleCalculator();
    private final LocalDateTime base = LocalDateTime.of(2026, 8, 21, 10, 30);

    @Test
    @DisplayName("间隔秒数计算下一次执行时间")
    void intervalSeconds() {
        assertThat(calculator.nextRun(ScheduledTaskCycleType.INTERVAL_SECONDS, 30,
                null, null, null, null, base))
                .isEqualTo(base.plusSeconds(30));
    }

    @Test
    @DisplayName("间隔分钟小时天数计算下一次执行时间")
    void intervalUnits() {
        assertThat(calculator.nextRun(ScheduledTaskCycleType.INTERVAL_MINUTES, 15,
                null, null, null, null, base)).isEqualTo(base.plusMinutes(15));
        assertThat(calculator.nextRun(ScheduledTaskCycleType.INTERVAL_HOURS, 2,
                null, null, null, null, base)).isEqualTo(base.plusHours(2));
        assertThat(calculator.nextRun(ScheduledTaskCycleType.INTERVAL_DAYS, 3,
                null, null, null, null, base)).isEqualTo(base.plusDays(3));
    }

    @Test
    @DisplayName("每天按时间计算下一次执行时间")
    void daily() {
        assertThat(calculator.nextRun(ScheduledTaskCycleType.DAILY, null,
                LocalTime.of(9, 0), null, null, null, base))
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 9, 0));
    }

    @Test
    @DisplayName("每周按星期和时间计算下一次执行时间")
    void weekly() {
        assertThat(calculator.nextRun(ScheduledTaskCycleType.WEEKLY, null,
                LocalTime.of(9, 0), 1, null, null, base))
                .isEqualTo(LocalDateTime.of(2026, 8, 24, 9, 0));
    }

    @Test
    @DisplayName("每月超过当月天数时按月末执行")
    void monthlyClampsToMonthEnd() {
        LocalDateTime march = LocalDateTime.of(2026, 2, 20, 10, 30);
        assertThat(calculator.nextRun(ScheduledTaskCycleType.MONTHLY, null,
                LocalTime.of(9, 0), null, 31, null, march))
                .isEqualTo(LocalDateTime.of(2026, 2, 28, 9, 0));
    }

    @Test
    @DisplayName("每年按月份日期和时间计算下一次执行时间")
    void yearly() {
        assertThat(calculator.nextRun(ScheduledTaskCycleType.YEARLY, null,
                LocalTime.of(9, 0), null, 1, 12, base))
                .isEqualTo(LocalDateTime.of(2026, 12, 1, 9, 0));
    }
}
