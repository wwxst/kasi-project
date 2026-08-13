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

    @Test
    @DisplayName("超级管理员可在个人资料修改账号和资料")
    void updateSuperAdminProfile() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"username":"kasiadmin2","realName":"系统负责人",
                                 "mobile":" 13800138001 ","email":" ADMIN@EXAMPLE.COM ",
                                 "avatarUrl":"https://example.com/admin.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("kasiadmin2"))
                .andExpect(jsonPath("$.data.realName").value("系统负责人"))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通管理员可修改本人展示资料且原Token继续有效")
    void updateOrdinaryAdminDisplayProfileKeepsTokenValid() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"username":"operator","realName":"运营负责人",
                                 "mobile":null,"email":null,
                                 "avatarUrl":"https://example.com/operator.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("运营负责人"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(0));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("运营负责人"));
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("正确账号密码登录成功")
    void loginSuccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"kasiadmin","password":"kasi123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.admin.username").value("kasiadmin"));
    }

    @Test
    @DisplayName("错误密码登录失败")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"kasiadmin","password":"wrongpassword"}
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
                                {"account":"nonexistent","password":"kasi123456"}
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
                                {"account":"disabledadmin","password":"kasi123456"}
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
                .andExpect(jsonPath("$.data.username").value("kasiadmin"))
                .andExpect(jsonPath("$.data.realName").value("系统管理员"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(1));
    }

    @Test
    @DisplayName("当前管理员只返回真实姓名")
    void getCurrentAdminReturnsRealNameWithoutNickname() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("kasiadmin"))
                .andExpect(jsonPath("$.data.realName").value("系统管理员"));
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
                                {"oldPassword":"kasi123456","newPassword":"newpass123","confirmPassword":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 用新密码登录验证
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"kasiadmin","password":"newpass123"}
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
                                {"oldPassword":"kasi123456","newPassword":"kasi123456","confirmPassword":"kasi123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }
}
