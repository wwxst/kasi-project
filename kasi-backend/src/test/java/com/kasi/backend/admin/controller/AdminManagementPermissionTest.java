package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员管理权限")
class AdminManagementPermissionTest extends BaseAuthTest {

    @Test
    @DisplayName("未登录访问管理员管理返回401")
    void anonymousCannotAccessManagement() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("推广用户访问管理员管理返回403")
    void userCannotAccessManagement() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("普通管理员访问管理员管理返回403")
    void ordinaryAdminCannotAccessManagement() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("超级管理员可以访问管理员管理")
    void superAdminCanAccessManagement() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
