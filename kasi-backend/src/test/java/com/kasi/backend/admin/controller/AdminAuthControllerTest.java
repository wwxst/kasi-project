package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Base64;

import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 管理员认证接口测试
 */
@DisplayName("管理员认证")
class AdminAuthControllerTest extends BaseAuthTest {

    @Test
    @DisplayName("管理员上传本人头像后原Token继续有效")
    void uploadOwnAvatarKeepsTokenValid() throws Exception {
        String token = loginAsAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", pngBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/admin/auth/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl", startsWith("/uploads/admin-avatars/")));

        String avatarUrl = jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM sys_admin_user WHERE username = ?", String.class, ADMIN_USERNAME);
        assertThat(avatarUrl).startsWith("/uploads/admin-avatars/");
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl));
    }

    @Test
    @DisplayName("管理员上传伪图片时返回头像格式错误")
    void uploadOwnAvatarRejectsInvalidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/admin/auth/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2012));
    }

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
                                {"newPassword":"newpass123","confirmPassword":"newpass123"}
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
    @DisplayName("不提交原密码也能修改密码")
    void changePasswordWithoutOldPassword() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"newPassword":"newpass123","confirmPassword":"newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
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
                                {"newPassword":"kasi123456","confirmPassword":"kasi123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005));
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
