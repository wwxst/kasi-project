package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.service.PromotionProjectService;
import com.kasi.backend.promotion.vo.PromotionProjectCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/promotion/projects")
@RequiredArgsConstructor
public class UserPromotionProjectController {
    private final PromotionProjectService projectService;

    @GetMapping
    public ApiResponse<List<PromotionProjectCardVO>> listEnabled() {
        return ApiResponse.success(projectService.listEnabled());
    }
}
