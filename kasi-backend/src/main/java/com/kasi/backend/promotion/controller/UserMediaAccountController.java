package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountStatusDTO;
import com.kasi.backend.promotion.service.MediaAccountService;
import com.kasi.backend.promotion.vo.MediaAccountDetailVO;
import com.kasi.backend.promotion.vo.MediaAccountVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/promotion/media-accounts")
@RequiredArgsConstructor
public class UserMediaAccountController {
    private final MediaAccountService mediaAccountService;

    @GetMapping
    public ApiResponse<List<MediaAccountVO>> getMine() {
        return ApiResponse.success(mediaAccountService.getMine(AuthContextHolder.getUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaAccountDetailVO> getMineById(@PathVariable Long id) {
        return ApiResponse.success(mediaAccountService.getMineById(AuthContextHolder.getUserId(), id));
    }

    @PostMapping
    public ApiResponse<MediaAccountDetailVO> create(@Valid @RequestBody CreateMediaAccountDTO request) {
        return ApiResponse.success(mediaAccountService.create(AuthContextHolder.getUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MediaAccountDetailVO> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateMediaAccountDTO request) {
        return ApiResponse.success(mediaAccountService.update(AuthContextHolder.getUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateMediaAccountStatusDTO request) {
        mediaAccountService.updateStatus(AuthContextHolder.getUserId(), id, request);
        return ApiResponse.successMessage("媒体账号状态修改成功");
    }

    @PostMapping("/{id}/filings/{providerId}")
    public ApiResponse<MediaFilingVO> submitOrRetry(@PathVariable Long id, @PathVariable Long providerId) {
        return ApiResponse.success(mediaAccountService.submitOrRetry(AuthContextHolder.getUserId(), id, providerId));
    }
}
