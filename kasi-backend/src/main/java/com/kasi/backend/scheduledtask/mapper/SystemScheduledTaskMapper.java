package com.kasi.backend.scheduledtask.mapper;

import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface SystemScheduledTaskMapper {
    List<SystemScheduledTask> findAll();

    SystemScheduledTask findByTaskCode(@Param("taskCode") ScheduledTaskCode taskCode);

    List<SystemScheduledTask> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int updateConfig(@Param("taskCode") ScheduledTaskCode taskCode,
                     @Param("description") String description,
                     @Param("cycleType") String cycleType,
                     @Param("intervalValue") Integer intervalValue,
                     @Param("intervalHoursPart") Integer intervalHoursPart,
                     @Param("intervalMinutesPart") Integer intervalMinutesPart,
                     @Param("timeOfDay") LocalTime timeOfDay,
                     @Param("dayOfWeek") Integer dayOfWeek,
                     @Param("dayOfMonth") Integer dayOfMonth,
                     @Param("monthOfYear") Integer monthOfYear,
                     @Param("enabled") boolean enabled,
                     @Param("nextRunAt") LocalDateTime nextRunAt);

    default int updateConfig(ScheduledTaskCode taskCode, String description,
                             String cycleType, Integer intervalValue,
                             LocalTime timeOfDay, Integer dayOfWeek,
                             Integer dayOfMonth, Integer monthOfYear,
                             boolean enabled, LocalDateTime nextRunAt) {
        return updateConfig(taskCode, description, cycleType, intervalValue,
                null, null, timeOfDay, dayOfWeek, dayOfMonth, monthOfYear,
                enabled, nextRunAt);
    }

    int claimLease(@Param("taskCode") ScheduledTaskCode taskCode,
                   @Param("owner") String owner,
                   @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int completeRun(@Param("taskCode") ScheduledTaskCode taskCode,
                    @Param("owner") String owner,
                    @Param("nextRunAt") LocalDateTime nextRunAt);

    default int claimLease(String taskCode, String owner, LocalDateTime now, LocalDateTime leaseUntil) {
        return claimLease(ScheduledTaskCode.valueOf(taskCode), owner, now, leaseUntil);
    }
    default int completeRun(String taskCode, String owner, LocalDateTime nextRunAt) {
        return completeRun(ScheduledTaskCode.valueOf(taskCode), owner, nextRunAt);
    }
}
