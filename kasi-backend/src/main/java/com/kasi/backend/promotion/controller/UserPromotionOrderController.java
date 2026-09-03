package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.PromotionOrderMonthQueryDTO;
import com.kasi.backend.promotion.service.PromotionOrderUserService;
import com.kasi.backend.promotion.vo.PromotionMonthlyCommissionVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderPageVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/promotion/orders")
@RequiredArgsConstructor
public class UserPromotionOrderController {
    private final PromotionOrderUserService userService;

    @GetMapping
    public ApiResponse<UserPromotionOrderPageVO> getPage(@Valid PromotionOrderMonthQueryDTO query) {
        return ApiResponse.success(userService.getPage(AuthContextHolder.getUserId(), query));
    }

    @GetMapping("/monthly")
    public ApiResponse<PromotionMonthlyCommissionVO> getMonthly(@Valid PromotionOrderMonthQueryDTO query) {
        return ApiResponse.success(userService.getMonthly(AuthContextHolder.getUserId(), query));
    }

}
