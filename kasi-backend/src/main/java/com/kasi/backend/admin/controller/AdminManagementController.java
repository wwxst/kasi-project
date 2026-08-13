package com.kasi.backend.admin.controller;

import com.kasi.backend.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/management")
public class AdminManagementController {

    @GetMapping
    public ApiResponse<Void> getPage() {
        return ApiResponse.success();
    }
}
