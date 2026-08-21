package com.kasi.backend.scheduledtask.mapper;

import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemScheduledTaskMapper {
    List<SystemScheduledTask> findAll();

    SystemScheduledTask findByTaskCode(@Param("taskCode") ScheduledTaskCode taskCode);

    List<SystemScheduledTask> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int updateConfig(@Param("taskCode") ScheduledTaskCode taskCode,
                     @Param("description") String description,
                     @Param("intervalMinutes") int intervalMinutes,
                     @Param("enabled") boolean enabled,
                     @Param("nextRunAt") LocalDateTime nextRunAt);

    int claimLease(@Param("id") Long id,
                   @Param("owner") String owner,
                   @Param("now") LocalDateTime now,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    int completeRun(@Param("id") Long id,
                    @Param("owner") String owner,
                    @Param("nextRunAt") LocalDateTime nextRunAt);
}
