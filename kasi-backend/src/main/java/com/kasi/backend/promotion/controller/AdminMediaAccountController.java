package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.dto.ResolveFilingSubmissionDTO;
import com.kasi.backend.promotion.dto.UpdateManualFilingStatusDTO;
import com.kasi.backend.promotion.service.MediaAccountAdminService;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/promotion/media-accounts")
@RequiredArgsConstructor
public class AdminMediaAccountController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final MediaAccountAdminService mediaAccountAdminService;

    @GetMapping
    public ApiResponse<AdminMediaAccountPageVO> getPage(@Valid AdminMediaAccountPageQueryDTO query) {
        return ApiResponse.success(mediaAccountAdminService.getPage(query));
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export(@Valid AdminMediaAccountPageQueryDTO query) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=media-account-filings.xlsx")
                .body(mediaAccountAdminService.exportXlsx(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminMediaAccountDetailVO> getById(@PathVariable Long id) {
        return ApiResponse.success(mediaAccountAdminService.getById(id));
    }

    @PostMapping("/{id}/filings/{providerId}/retry")
    public ApiResponse<MediaFilingVO> retry(@PathVariable Long id, @PathVariable Long providerId) {
        return ApiResponse.success(mediaAccountAdminService.retry(id, providerId));
    }

    @PatchMapping("/{id}/filings/{providerId}/status")
    public ApiResponse<MediaFilingVO> updateManualStatus(
            @PathVariable Long id, @PathVariable Long providerId,
            @Valid @RequestBody UpdateManualFilingStatusDTO request) {
        return ApiResponse.success(mediaAccountAdminService.updateManualStatus(
                AuthContextHolder.getAdminId(), id, providerId, request));
    }

    @PostMapping("/{id}/filings/{providerId}/submission-resolution")
    public ApiResponse<MediaFilingVO> resolveSubmission(
            @PathVariable Long id, @PathVariable Long providerId,
            @Valid @RequestBody ResolveFilingSubmissionDTO request) {
        return ApiResponse.success(mediaAccountAdminService.resolveSubmission(
                AuthContextHolder.getAdminId(), id, providerId, request.getResolution()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mediaAccountAdminService.delete(id);
        return ApiResponse.successMessage("媒体账号删除成功");
    }

}
