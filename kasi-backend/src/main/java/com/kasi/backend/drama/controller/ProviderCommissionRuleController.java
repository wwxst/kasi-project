package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.CreateCommissionRuleDTO;
import com.kasi.backend.drama.dto.UpdateCommissionRuleDTO;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.drama.vo.ProviderCommissionRuleVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/drama/providers/{providerId}/commission-rules")
@RequiredArgsConstructor
public class ProviderCommissionRuleController {

    private final ProviderCommissionRuleService commissionRuleService;

    @GetMapping
    public ApiResponse<List<ProviderCommissionRuleVO>> getRules(
            @PathVariable @Positive Long providerId) {
        return ApiResponse.success(commissionRuleService.getRules(providerId));
    }

    @PostMapping
    public ApiResponse<ProviderCommissionRuleVO> create(
            @PathVariable @Positive Long providerId,
            @Valid @RequestBody CreateCommissionRuleDTO request) {
        return ApiResponse.success(commissionRuleService.create(
                AuthContextHolder.getAdminId(), providerId, request));
    }

    @PutMapping("/{ruleId}")
    public ApiResponse<ProviderCommissionRuleVO> update(
            @PathVariable @Positive Long providerId,
            @PathVariable @Positive Long ruleId,
            @Valid @RequestBody UpdateCommissionRuleDTO request) {
        return ApiResponse.success(commissionRuleService.update(
                AuthContextHolder.getAdminId(), providerId, ruleId, request));
    }

}
