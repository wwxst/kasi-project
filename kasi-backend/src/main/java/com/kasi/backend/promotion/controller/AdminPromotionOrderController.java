package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.PromotionOrderPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.service.PromotionOrderAdminService;
import com.kasi.backend.promotion.vo.PromotionOrderPageVO;
import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotion/orders")
@RequiredArgsConstructor
public class AdminPromotionOrderController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final PromotionOrderAdminService adminService;

    @PostMapping("/sync")
    public ApiResponse<PromotionOrderSyncResultVO> sync(@Valid @RequestBody PromotionOrderSyncDTO request) {
        return ApiResponse.success(adminService.sync(request));
    }

    @GetMapping
    public ApiResponse<PromotionOrderPageVO> getPage(@Valid PromotionOrderPageQueryDTO query) {
        return ApiResponse.success(adminService.getPage(query));
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export(@Valid PromotionOrderPageQueryDTO query) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=promotion-orders.xlsx")
                .body(adminService.exportXlsx(query));
    }
}
