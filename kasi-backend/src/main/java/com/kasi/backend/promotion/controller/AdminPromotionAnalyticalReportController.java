package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.PromotionAnalyticalReportPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionAnalyticalReportSyncDTO;
import com.kasi.backend.promotion.service.PromotionAnalyticalReportAdminService;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportPageVO;
import com.kasi.backend.promotion.vo.PromotionAnalyticalReportSyncResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotion/analytical-reports")
@RequiredArgsConstructor
public class AdminPromotionAnalyticalReportController {
    private final PromotionAnalyticalReportAdminService adminService;

    @PostMapping("/sync")
    public ApiResponse<PromotionAnalyticalReportSyncResultVO> sync(
            @Valid @RequestBody PromotionAnalyticalReportSyncDTO request) {
        return ApiResponse.success(adminService.sync(request));
    }

    @GetMapping
    public ApiResponse<PromotionAnalyticalReportPageVO> getPage(
            @Valid PromotionAnalyticalReportPageQueryDTO query) {
        return ApiResponse.success(adminService.getPage(query));
    }
}
