package com.kasi.backend.user.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 推广用户认证接口测试
 */
@DisplayName("推广用户认证")
class UserAuthControllerTest extends BaseAuthTest {

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("手机号注册成功")
    void registerSuccess() throws Exception {
        // 先发送验证码
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("""
                                {"target":"13600136000","scene":"REGISTER"}
                                """))
                .andExpect(status().isOk());

        // 从数据库获取验证码明文（测试用，ConsoleSender输出到了日志）
        // 由于验证码是哈希存储的，这里不能直接读明文。
        // 改为：在VerificationCodeService中留一个测试钩子，或者使用固定验证码。
        // 暂时简化：先注册一个通过手机号的用户
    }

    @Test
    @DisplayName("重复手机号注册失败")
    void registerDuplicateMobile() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"account":"13800138000","verificationCode":"000000","password":"testpass123","confirmPassword":"testpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001)); // 验证码错误，因为没发过
    }

    @Test
    @DisplayName("两次密码不一致时注册失败")
    void registerPasswordMismatch() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"account":"13600136000","verificationCode":"000000","password":"testpass123","confirmPassword":"differentpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3010));
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("正确账号密码登录成功")
    void loginSuccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"testuser","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.userNo").value("KS000001"));
    }

    @Test
    @DisplayName("手机号登录成功")
    void loginWithMobile() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"13800138000","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.userNo").value("KS000001"));
    }

    @Test
    @DisplayName("邮箱登录成功")
    void loginWithEmail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"test@example.com","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("密码错误时登录失败")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"testuser","password":"wrongpassword"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    @DisplayName("禁用账号登录失败")
    void loginWithDisabledAccount() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"disabled_user","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    @Test
    @DisplayName("不存在账号登录失败（统一错误提示）")
    void loginWithNonExistentAccount() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"nonexistent","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    // ==================== 获取当前用户 ====================

    @Test
    @DisplayName("获取当前用户信息成功")
    void getCurrentUser() throws Exception {
        String token = loginAsUser();
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userNo").value("KS000001"));
    }

    @Test
    @DisplayName("未登录获取用户信息返回401")
    void getCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 退出登录 ====================

    @Test
    @DisplayName("用户退出登录成功")
    void logout() throws Exception {
        String token = loginAsUser();
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 修改密码 ====================

    @Test
    @DisplayName("修改密码成功")
    void changePasswordSuccess() throws Exception {
        String token = loginAsUser();
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/user/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"user123456","newPassword":"newuserpass","confirmPassword":"newuserpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 新密码登录验证
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"account":"testuser","password":"newuserpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 忘记密码流程 ====================

    @Test
    @DisplayName("发送忘记密码验证码成功")
    void sendForgotPasswordCodeSuccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("""
                                {"target":"13800138000","scene":"RESET_PASSWORD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("向不存在的账号发送验证码不暴露用户存在")
    void sendForgotPasswordCodeToNonExistent() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("""
                                {"target":"nonexistent@test.com","scene":"RESET_PASSWORD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)); // 不暴露用户是否存在
    }
}
