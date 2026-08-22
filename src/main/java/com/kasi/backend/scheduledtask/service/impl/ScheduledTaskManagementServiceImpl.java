package com.kasi.backend.scheduledtask.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.scheduledtask.dto.UpdateScheduledTaskDTO;
import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.scheduledtask.service.ScheduledTaskManagementService;
import com.kasi.backend.scheduledtask.vo.ScheduledTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduledTaskManagementServiceImpl implements ScheduledTaskManagementService {
    private final SystemScheduledTaskMapper taskMapper;
    private final Clock clock;
    private final com.kasi.backend.scheduledtask.service.ScheduledTaskScheduleCalculator scheduleCalculator;

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledTaskVO> getTasks() {
        return taskMapper.findAll().stream().map(ScheduledTaskVO::from).toList();
    }

    @Override
    @Transactional
    public ScheduledTaskVO updateTask(ScheduledTaskCode taskCode, UpdateScheduledTaskDTO request) {
        SystemScheduledTask existing = taskMapper.findByTaskCode(taskCode);
        if (existing == null) {
            throw new BusinessException(ErrorCode.SCHEDULED_TASK_NOT_FOUND);
        }
        ScheduledTaskCycleType cycleType = request.getCycleType() == null
                ? ScheduledTaskCycleType.INTERVAL_MINUTES : request.getCycleType();
        Integer intervalValue = request.getIntervalValue() == null
                ? request.getIntervalMinutes() : request.getIntervalValue();
        if (cycleType.name().startsWith("INTERVAL_")
                && (intervalValue == null || intervalValue < 1 || intervalValue > 1440)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime nextRunAt;
        try {
            nextRunAt = Boolean.TRUE.equals(request.getEnabled())
                    ? scheduleCalculator.nextRun(cycleType, intervalValue, request.getTimeOfDay(),
                    request.getDayOfWeek(), request.getDayOfMonth(), request.getMonthOfYear(), now)
                    : null;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int updated = taskMapper.updateConfig(taskCode, request.getDescription().trim(),
                cycleType.name(), intervalValue, request.getTimeOfDay(), request.getDayOfWeek(),
                request.getDayOfMonth(), request.getMonthOfYear(), request.getEnabled(), nextRunAt);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.SCHEDULED_TASK_NOT_FOUND);
        }
        return ScheduledTaskVO.from(taskMapper.findByTaskCode(taskCode));
    }
}
