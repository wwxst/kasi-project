package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.dto.RequestAllDramaContentSyncDTO;
import com.kasi.backend.drama.dto.RequestDramaContentBatchSyncDTO;
import com.kasi.backend.drama.dto.RequestDramaSyncDTO;
import com.kasi.backend.drama.dto.UpdateDramaLocalStatusDTO;
import com.kasi.backend.drama.dto.UpdateDramaPromotionMetadataDTO;
import com.kasi.backend.drama.service.DramaCatalogAdminService;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.drama.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/drama/catalog")
@RequiredArgsConstructor
public class AdminDramaCatalogController {
    private final DramaCatalogAdminService adminService;
    private final DramaCatalogSyncService syncService;
    private final DramaContentSyncService contentSyncService;
    private final com.kasi.backend.drama.service.DramaSyncRecordQueryService recordQueryService;

    @GetMapping
    public ApiResponse<DramaPageVO> getPage(@Valid DramaPageQueryDTO query) {
        return ApiResponse.success(adminService.getPage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<DramaDetailVO> getById(@PathVariable @Positive Long id) {
        return ApiResponse.success(adminService.getById(id));
    }

    @PostMapping("/sync")
    public ApiResponse<List<DramaSyncTaskVO>> requestSync(@Valid @RequestBody RequestDramaSyncDTO request) {
        return ApiResponse.success(syncService.requestSync(
                request.getProviderId(), request.getSyncType(), request.getLanguages()));
    }

    @GetMapping("/sync/status")
    public ApiResponse<List<DramaSyncStatusVO>> getSyncStatuses(@RequestParam @Positive Long providerId) {
        return ApiResponse.success(syncService.getStatuses(providerId).stream().map(DramaSyncStatusVO::from).toList());
    }

    @GetMapping("/sync/records")
    public ApiResponse<List<com.kasi.backend.drama.vo.DramaSyncRecordVO>> getSyncRecords(
            @RequestParam @Positive Long providerId) {
        return ApiResponse.success(recordQueryService.listCatalog(providerId));
    }

    @GetMapping("/sync/records/{runId}")
    public ApiResponse<List<com.kasi.backend.drama.vo.DramaSyncRecordDetailVO>> getSyncRecordDetails(
            @RequestParam @Positive Long providerId, @PathVariable String runId) {
        return ApiResponse.success(recordQueryService.catalogDetails(providerId, runId));
    }

    @GetMapping("/contents/sync/records")
    public ApiResponse<List<com.kasi.backend.drama.vo.DramaSyncRecordVO>> getContentSyncRecords(
            @RequestParam @Positive Long providerId) {
        return ApiResponse.success(recordQueryService.listContent(providerId));
    }

    @GetMapping("/contents/sync/records/{runId}")
    public ApiResponse<List<com.kasi.backend.drama.vo.DramaContentSyncRecordDetailVO>> getContentSyncRecordDetails(
            @RequestParam @Positive Long providerId, @PathVariable String runId) {
        return ApiResponse.success(recordQueryService.contentDetails(providerId, runId));
    }

    @PostMapping("/{id}/contents/sync")
    public ApiResponse<DramaContentSyncTaskVO> requestContentSync(@PathVariable @Positive Long id) {
        return ApiResponse.success(contentSyncService.request(id));
    }

    @PostMapping("/contents/sync")
    public ApiResponse<DramaContentSyncBatchVO> requestContentBatchSync(
            @Valid @RequestBody RequestDramaContentBatchSyncDTO request) {
        return ApiResponse.success(contentSyncService.requestBatch(request.getDramaIds()));
    }

    @PostMapping("/contents/sync/all")
    public ApiResponse<DramaContentSyncBatchVO> requestAllContentSync(
            @Valid @RequestBody RequestAllDramaContentSyncDTO request) {
        return ApiResponse.success(contentSyncService.requestAll(
                request.getProviderId(), request.getLanguage(), request.isMissingOnly()));
    }

    @GetMapping("/{id}/contents/sync/status")
    public ApiResponse<DramaContentSyncTaskVO> getContentSyncStatus(@PathVariable @Positive Long id) {
        return ApiResponse.success(contentSyncService.getStatus(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DramaDetailVO> updateLocalStatus(@PathVariable @Positive Long id,
                                                         @Valid @RequestBody UpdateDramaLocalStatusDTO request) {
        return ApiResponse.success(adminService.updateLocalStatus(id, request.getLocalStatus()));
    }

    @PutMapping("/{id}/promotion-metadata")
    public ApiResponse<DramaDetailVO> updatePromotionMetadata(@PathVariable @Positive Long id,
                                                                @Valid @RequestBody UpdateDramaPromotionMetadataDTO request) {
        return ApiResponse.success(adminService.updatePromotionMetadata(id, request.getCommissionScopes(),
                request.getPromotionDescription()));
    }
}
