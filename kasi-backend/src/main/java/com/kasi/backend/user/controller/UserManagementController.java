package com.kasi.backend.user.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.service.UserManagementService;
import com.kasi.backend.user.vo.UserDetailVO;
import com.kasi.backend.user.vo.UserPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/management")
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService userManagementService;

    @GetMapping
    public ApiResponse<UserPageVO> getPage(@Valid UserPageQueryDTO query) {
        return ApiResponse.success(userManagementService.getPage(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDetailVO> getById(@PathVariable Long id) {
        return ApiResponse.success(userManagementService.getById(id));
    }

    @PostMapping
    public ApiResponse<UserDetailVO> create(@Valid @RequestBody CreateUserDTO request) {
        return ApiResponse.success(userManagementService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserDTO request) {
        return ApiResponse.success(userManagementService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusDTO request) {
        userManagementService.updateStatus(id, request);
        return ApiResponse.successMessage("状态修改成功");
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetUserPasswordDTO request) {
        userManagementService.resetPassword(id, request);
        return ApiResponse.successMessage("密码重置成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userManagementService.delete(id);
        return ApiResponse.successMessage("推广用户删除成功");
    }
}
