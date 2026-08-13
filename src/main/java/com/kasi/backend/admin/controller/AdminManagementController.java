package com.kasi.backend.admin.controller;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.service.AdminManagementService;
import com.kasi.backend.admin.vo.AdminDetailVO;
import com.kasi.backend.admin.vo.AdminPageVO;
import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/management")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    @GetMapping
    public ApiResponse<AdminPageVO> getPage(@Valid AdminPageQueryDTO query) {
        return ApiResponse.success(adminManagementService.getPage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminDetailVO> getById(@PathVariable Long id) {
        return ApiResponse.success(adminManagementService.getById(id));
    }

    @PostMapping
    public ApiResponse<AdminDetailVO> create(@Valid @RequestBody CreateAdminDTO request) {
        return ApiResponse.success(adminManagementService.create(AuthContextHolder.getAdminId(), request));
    }
}
