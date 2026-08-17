package com.kasi.backend.provider.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.service.ProviderConnectionService;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/drama/providers")
@RequiredArgsConstructor
public class ProviderAdminController {

    private final ProviderConnectionService providerConnectionService;

    @GetMapping
    public ApiResponse<List<ProviderVO>> getProviders() {
        return ApiResponse.success(providerConnectionService.getProviders());
    }

    @PutMapping("/{providerId}/connection")
    public ApiResponse<ProviderConnectionVO> upsertConnection(
            @PathVariable Long providerId,
            @Valid @RequestBody UpsertProviderConnectionDTO request) {
        return ApiResponse.success(providerConnectionService.upsert(
                AuthContextHolder.getAdminId(), providerId, request));
    }

    @PostMapping("/{providerId}/connection/test")
    public ApiResponse<ProviderConnectionTestVO> testConnection(@PathVariable Long providerId) {
        return ApiResponse.success(providerConnectionService.testConnection(providerId));
    }
}
