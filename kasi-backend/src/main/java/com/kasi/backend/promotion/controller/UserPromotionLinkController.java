package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.dto.PromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.service.PromotionLinkService;
import com.kasi.backend.promotion.vo.PromotionLinkPageVO;
import com.kasi.backend.promotion.vo.PromotionLinkVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/promotion/links")
@RequiredArgsConstructor
public class UserPromotionLinkController {
    private final PromotionLinkService promotionLinkService;

    @GetMapping
    public ApiResponse<PromotionLinkPageVO> getMine(@Valid PromotionLinkPageQueryDTO query) {
        return ApiResponse.success(promotionLinkService.getMine(AuthContextHolder.getUserId(), query));
    }

    @PostMapping
    public ApiResponse<PromotionLinkVO> create(@Valid @RequestBody CreatePromotionLinkDTO request) {
        return ApiResponse.success(promotionLinkService.createOrRetry(AuthContextHolder.getUserId(), request));
    }
}
