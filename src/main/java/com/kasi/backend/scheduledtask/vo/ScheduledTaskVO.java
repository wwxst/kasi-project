package com.kasi.backend.scheduledtask.vo;

import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import lombok.Builder;
import lombok.Data;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import java.time.LocalTime;

@Data
@Builder
public class ScheduledTaskVO {
    private ScheduledTaskCode taskCode;
    private String title;
    private String description;
    private ScheduledTaskCycleType cycleType;
    private Integer intervalValue;
    private Integer intervalHoursPart;
    private Integer intervalMinutesPart;
    private LocalTime timeOfDay;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Integer monthOfYear;
    private Integer intervalMinutes;
    private Boolean enabled;

    public static ScheduledTaskVO from(SystemScheduledTask task) {
        return ScheduledTaskVO.builder()
                .taskCode(task.getTaskCode())
                .title(task.getTitle())
                .description(task.getDescription())
                .cycleType(task.getCycleType())
                .intervalValue(task.getIntervalValue())
                .intervalHoursPart(task.getIntervalHoursPart())
                .intervalMinutesPart(task.getIntervalMinutesPart())
                .timeOfDay(task.getTimeOfDay())
                .dayOfWeek(task.getDayOfWeek())
                .dayOfMonth(task.getDayOfMonth())
                .monthOfYear(task.getMonthOfYear())
                .intervalMinutes(task.getIntervalMinutes())
                .enabled(task.getEnabled())
                .build();
    }
}
