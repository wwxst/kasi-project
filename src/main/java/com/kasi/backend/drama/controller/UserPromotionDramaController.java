package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaPageVO;
import com.kasi.backend.drama.vo.DramaDetailVO;
import com.kasi.backend.drama.vo.DramaContentResourceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/promotion/dramas")
@RequiredArgsConstructor
public class UserPromotionDramaController {
    private final UserPromotionDramaService dramaService;

    @GetMapping
    public ApiResponse<DramaPageVO> getPublished(@Valid DramaPageQueryDTO query) {
        return ApiResponse.success(dramaService.getPublished(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<DramaDetailVO> getPublishedDetail(@PathVariable Long id) {
        return ApiResponse.success(dramaService.getPublishedDetail(id));
    }

    @GetMapping("/{id}/free-content")
    public ApiResponse<List<DramaContentResourceVO>> getFreeContent(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(dramaService.getFreeContent(id, refresh));
    }
}
