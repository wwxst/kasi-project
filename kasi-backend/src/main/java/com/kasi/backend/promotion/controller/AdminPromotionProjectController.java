package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.PromotionProjectPageQueryDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectDTO;
import com.kasi.backend.promotion.service.PromotionProjectService;
import com.kasi.backend.promotion.vo.PromotionProjectPageVO;
import com.kasi.backend.promotion.vo.PromotionProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/promotion/projects")
@RequiredArgsConstructor
public class AdminPromotionProjectController {
    private final PromotionProjectService projectService;

    @GetMapping
    public ApiResponse<PromotionProjectPageVO> getPage(@Valid PromotionProjectPageQueryDTO query) {
        return ApiResponse.success(projectService.getPage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionProjectVO> getById(@PathVariable Long id) {
        return ApiResponse.success(projectService.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PromotionProjectVO> create(
            @Valid @ModelAttribute UpsertPromotionProjectDTO request) {
        return ApiResponse.success(projectService.create(request, request.getCoverFile()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PromotionProjectVO> update(
            @PathVariable Long id, @Valid @ModelAttribute UpsertPromotionProjectDTO request) {
        return ApiResponse.success(projectService.update(id, request, request.getCoverFile()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.successMessage("项目删除成功");
    }
}
