package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.service.MediaAccountAdminService;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/promotion/media-accounts")
@RequiredArgsConstructor
public class AdminMediaAccountController {
    private final MediaAccountAdminService mediaAccountAdminService;

    @GetMapping
    public ApiResponse<AdminMediaAccountPageVO> getPage(@Valid AdminMediaAccountPageQueryDTO query) {
        return ApiResponse.success(mediaAccountAdminService.getPage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminMediaAccountDetailVO> getById(@PathVariable Long id) {
        return ApiResponse.success(mediaAccountAdminService.getById(id));
    }

    @PostMapping("/{id}/filings/{providerId}/retry")
    public ApiResponse<MediaFilingVO> retry(@PathVariable Long id, @PathVariable Long providerId) {
        return ApiResponse.success(mediaAccountAdminService.retry(id, providerId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mediaAccountAdminService.delete(id);
        return ApiResponse.successMessage("媒体账号删除成功");
    }

}
