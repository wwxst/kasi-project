package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.scheduledtask.dto.UpdateScheduledTaskDTO;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.vo.ScheduledTaskVO;

import java.util.List;

public interface ScheduledTaskManagementService {
    List<ScheduledTaskVO> getTasks();

    ScheduledTaskVO updateTask(ScheduledTaskCode taskCode, UpdateScheduledTaskDTO request);
}
