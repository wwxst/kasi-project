package com.kasi.backend.admin.controller;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminStatusDTO;
import com.kasi.backend.admin.dto.ResetAdminPasswordDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping("/{id}")
    public ApiResponse<AdminDetailVO> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateAdminDTO request) {
        return ApiResponse.success(adminManagementService.update(
                AuthContextHolder.getAdminId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateAdminStatusDTO request) {
        adminManagementService.updateStatus(AuthContextHolder.getAdminId(), id, request);
        return ApiResponse.successMessage("状态修改成功");
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetAdminPasswordDTO request) {
        adminManagementService.resetPassword(AuthContextHolder.getAdminId(), id, request);
        return ApiResponse.successMessage("密码重置成功");
    }

    @PutMapping("/{id}/avatar")
    public ApiResponse<AdminDetailVO> updateAvatar(@PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(adminManagementService.updateAvatar(
                AuthContextHolder.getAdminId(), id, file));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminManagementService.delete(AuthContextHolder.getAdminId(), id);
        return ApiResponse.successMessage("管理员删除成功");
    }
}
