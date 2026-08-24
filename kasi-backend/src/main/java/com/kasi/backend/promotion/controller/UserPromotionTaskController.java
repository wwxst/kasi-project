package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.CreatePromotionTaskDTO;
import com.kasi.backend.promotion.dto.PromotionTaskPageQueryDTO;
import com.kasi.backend.promotion.service.PromotionTaskService;
import com.kasi.backend.promotion.vo.PromotionTaskPageVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/promotion/tasks")
@RequiredArgsConstructor
public class UserPromotionTaskController {
    private final PromotionTaskService taskService;

    @GetMapping
    public ApiResponse<PromotionTaskPageVO> list(@Valid PromotionTaskPageQueryDTO query) {
        return ApiResponse.success(taskService.getMine(AuthContextHolder.getUserId(), query));
    }

    @PostMapping
    public ApiResponse<PromotionTaskPageVO> create(@Valid @RequestBody CreatePromotionTaskDTO request) {
        return ApiResponse.success(taskService.create(AuthContextHolder.getUserId(), request));
    }
}
