package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.service.MediaAccountService;
import com.kasi.backend.promotion.vo.MediaAccountDetailVO;
import com.kasi.backend.promotion.vo.MediaAccountVO;
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

}
