package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.AdminPromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.service.PromotionLinkAdminService;
import com.kasi.backend.promotion.vo.AdminPromotionLinkPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotion/links")
@RequiredArgsConstructor
public class AdminPromotionLinkController {
    private final PromotionLinkAdminService adminService;

    @GetMapping
    public ApiResponse<AdminPromotionLinkPageVO> getPage(@Valid AdminPromotionLinkPageQueryDTO query) {
        return ApiResponse.success(adminService.getPage(query));
    }
}
