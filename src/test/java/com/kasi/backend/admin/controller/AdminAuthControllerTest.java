package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理员认证接口测试
 */
@DisplayName("管理员认证")
class AdminAuthControllerTest extends BaseAuthTest {

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("正确账号密码登录成功")
    void loginSuccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"admin","password":"admin123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.admin.username").value("admin"));
    }

    @Test
    @DisplayName("错误密码登录失败")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"admin","password":"wrongpassword"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    @DisplayName("不存在账号登录失败")
    void loginWithNonExistentAccount() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"nonexistent","password":"admin123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    @DisplayName("禁用账号登录失败")
    void loginWithDisabledAccount() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"disabled_admin","password":"admin123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    // ==================== 获取当前管理员 ====================

    @Test
    @DisplayName("获取当前管理员信息成功")
    void getCurrentAdmin() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.nickname").value("超级管理员"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(1));
    }

    @Test
    @DisplayName("未登录获取管理员信息返回401")
    void getCurrentAdminWithoutToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 退出登录 ====================

    @Test
    @DisplayName("管理员退出登录成功")
    void logout() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 修改密码 ====================

    @Test
    @DisplayName("修改密码成功")
    void changePasswordSuccess() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"admin123456","newPassword":"newpass123","confirmPassword":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 用新密码登录验证
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"admin","password":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("旧密码错误时修改密码失败")
    void changePasswordWithWrongOldPassword() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"wrongoldpass","newPassword":"newpass123","confirmPassword":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));
    }

    @Test
    @DisplayName("新密码与旧密码相同时修改失败")
    void changePasswordSameAsOld() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"admin123456","newPassword":"admin123456","confirmPassword":"admin123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }
}
