package com.kasi.backend.scheduledtask.entity;

import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemScheduledTask {
    private Long id;
    private ScheduledTaskCode taskCode;
    private String title;
    private String description;
    private Integer intervalMinutes;
    private Boolean enabled;
    private LocalDateTime nextRunAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
