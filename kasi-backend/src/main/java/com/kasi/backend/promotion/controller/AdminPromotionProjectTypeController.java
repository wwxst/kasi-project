package com.kasi.backend.promotion.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectTypeDTO;
import com.kasi.backend.promotion.service.PromotionProjectTypeService;
import com.kasi.backend.promotion.vo.PromotionProjectTypeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/promotion/project-types")
@RequiredArgsConstructor
public class AdminPromotionProjectTypeController {
    private final PromotionProjectTypeService projectTypeService;

    @GetMapping
    public ApiResponse<List<PromotionProjectTypeVO>> list() {
        return ApiResponse.success(projectTypeService.list());
    }

    @PostMapping
    public ApiResponse<PromotionProjectTypeVO> create(
            @Valid @RequestBody UpsertPromotionProjectTypeDTO request) {
        return ApiResponse.success(projectTypeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromotionProjectTypeVO> update(
            @PathVariable Long id, @Valid @RequestBody UpsertPromotionProjectTypeDTO request) {
        return ApiResponse.success(projectTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectTypeService.delete(id);
        return ApiResponse.successMessage("项目类型删除成功");
    }
}
