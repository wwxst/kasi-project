package com.kasi.backend.scheduledtask.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.scheduledtask.dto.UpdateScheduledTaskDTO;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCode;
import com.kasi.backend.scheduledtask.service.ScheduledTaskManagementService;
import com.kasi.backend.scheduledtask.vo.ScheduledTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {
    private final ScheduledTaskManagementService managementService;

    @GetMapping
    public ApiResponse<List<ScheduledTaskVO>> getTasks() {
        return ApiResponse.success(managementService.getTasks());
    }

    @PutMapping("/{taskCode}")
    public ApiResponse<ScheduledTaskVO> updateTask(
            @PathVariable ScheduledTaskCode taskCode,
            @Valid @RequestBody UpdateScheduledTaskDTO request) {
        return ApiResponse.success(managementService.updateTask(taskCode, request));
    }
}
