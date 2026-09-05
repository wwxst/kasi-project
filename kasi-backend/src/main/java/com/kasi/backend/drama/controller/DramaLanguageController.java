package com.kasi.backend.drama.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.service.DramaLanguageService;
import com.kasi.backend.drama.vo.DramaLanguageOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drama/languages")
@RequiredArgsConstructor
public class DramaLanguageController {
    private final DramaLanguageService languageService;

    @GetMapping
    public ApiResponse<List<DramaLanguageOptionVO>> listOptions() {
        return ApiResponse.success(languageService.listOptions());
    }
}
