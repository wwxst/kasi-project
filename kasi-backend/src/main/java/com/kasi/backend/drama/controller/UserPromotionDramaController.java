package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/promotion/dramas")
@RequiredArgsConstructor
public class UserPromotionDramaController {
    private final UserPromotionDramaService dramaService;

    @GetMapping
    public ApiResponse<DramaPageVO> getPublished(@Valid DramaPageQueryDTO query) {
        return ApiResponse.success(dramaService.getPublished(query));
    }
}
