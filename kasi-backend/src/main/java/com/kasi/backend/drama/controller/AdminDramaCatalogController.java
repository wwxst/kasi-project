package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.dto.RequestDramaSyncDTO;
import com.kasi.backend.drama.dto.UpdateDramaLocalStatusDTO;
import com.kasi.backend.drama.dto.UpdateDramaPromotionMetadataDTO;
import com.kasi.backend.drama.service.DramaCatalogAdminService;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
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
