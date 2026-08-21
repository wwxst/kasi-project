package com.kasi.backend.scheduledtask.vo;

import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduledTaskVO {
    private ScheduledTaskCode taskCode;
    private String title;
    private String description;
    private Integer intervalMinutes;
    private Boolean enabled;

    public static ScheduledTaskVO from(SystemScheduledTask task) {
        return ScheduledTaskVO.builder()
                .taskCode(task.getTaskCode())
                .title(task.getTitle())
                .description(task.getDescription())
                .intervalMinutes(task.getIntervalMinutes())
                .enabled(task.getEnabled())
                .build();
    }
}
