package com.kasi.backend.scheduledtask.entity;

import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class SystemScheduledTask {
    private Long id;
    private ScheduledTaskCode taskCode;
    private String title;
    private String description;
    private ScheduledTaskCycleType cycleType;
    private Integer intervalValue;
    private LocalTime timeOfDay;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private Integer monthOfYear;
    private Integer intervalMinutes;
    private Boolean enabled;
    private LocalDateTime nextRunAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
