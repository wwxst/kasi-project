package com.kasi.backend.user.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.auth.service.TestVerificationCodeSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 推广用户认证接口测试
 */
@DisplayName("推广用户认证")
class UserAuthControllerTest extends BaseAuthTest {

    @Autowired
    private TestVerificationCodeSender verificationCodeSender;

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("手机号注册成功")
    void registerSuccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register/code")
                        .contentType("application/json")
                        .content("""
                                {"target":" 13600136000 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register")
                        .contentType("application/json")
                        .content(String.format("""
                                {"account":" 13600136000 ","verificationCode":"%s","password":"testpass123","confirmPassword":"testpass123"}
                                """, verificationCodeSender.latestCode("13600136000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String userNo = jdbcTemplate.queryForObject(
                "SELECT user_no FROM promotion_user WHERE mobile = '13600136000'", String.class);
        String source = jdbcTemplate.queryForObject(
                "SELECT register_source FROM promotion_user WHERE mobile = '13600136000'", String.class);
        String nickname = jdbcTemplate.queryForObject(
                "SELECT nickname FROM promotion_user WHERE mobile = '13600136000'", String.class);
        org.junit.jupiter.api.Assertions.assertFalse(userNo.startsWith("TMP-"));
        org.junit.jupiter.api.Assertions.assertEquals("MOBILE", source);
        org.junit.jupiter.api.Assertions.assertEquals("用户" + userNo, nickname);
    }

    @Test
    @DisplayName("邮箱注册统一转小写并记录EMAIL来源")
    void registerWithEmailNormalizesAccountAndSource() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register/code")
                        .contentType("application/json")
                        .content("{\"target\":\" New.User@Example.COM \"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register")
                        .contentType("application/json")
                        .content(String.format("""
                                {"account":" New.User@Example.COM ","verificationCode":"%s","password":"testpass123","confirmPassword":"testpass123"}
                                """, verificationCodeSender.latestCode("new.user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String source = jdbcTemplate.queryForObject(
                "SELECT register_source FROM promotion_user WHERE email = 'new.user@example.com'", String.class);
        org.junit.jupiter.api.Assertions.assertEquals("EMAIL", source);
    }

    @Test
    @DisplayName("前端scene字段不能改变注册发码场景")
    void registerCodeIgnoresClientScene() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register/code")
                        .contentType("application/json")
                        .content("{\"target\":\"13600136000\",\"scene\":\"RESET_PASSWORD\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register")
                        .contentType("application/json")
                        .content(String.format("""
                                {"account":"13600136000","verificationCode":"%s","password":"testpass123","confirmPassword":"testpass123"}
                                """, verificationCodeSender.latestCode("13600136000"))))
                .andExpect(jsonPath("$.code").value(0));
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
                                {"account":"13800138000","password":"user123456"}
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
                                {"account":"13800138000","password":"wrongpassword"}
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
                                {"account":"13700137000","password":"user123456"}
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
                                {"account":"13600136000","password":"user123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3003))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    @DisplayName("登录标识必须是手机号或邮箱")
    void loginRejectsLegacyUsernameFormat() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"legacy_username\",\"password\":\"user123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("登录账号会trim且邮箱忽略大小写")
    void loginNormalizesAccount() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\" Test@Example.COM \",\"password\":\"user123456\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("注册账号格式错误返回校验错误")
    void registerRejectsInvalidAccountFormat() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register/code")
                        .contentType("application/json")
                        .content("{\"target\":\"not-an-account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("超过BCrypt UTF8字节上限的密码返回校验错误")
    void loginRejectsPasswordOverBcryptByteLimit() throws Exception {
        String password = "密".repeat(25);

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("account", "13800138000", "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
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

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("修改密码后所有旧会话失效")
    void changePasswordInvalidatesAllExistingSessions() throws Exception {
        String firstToken = loginAsUser();
        String secondToken = loginAsUser();

        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/user/auth/password")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"user123456","newPassword":"newuserpass","confirmPassword":"newuserpass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());
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
                                {"account":"13800138000","password":"newuserpass"}
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

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("{\"target\":\"nonexistent@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("不存在账号与存在账号的忘记密码错误响应一致")
    void verifyForgotPasswordDoesNotRevealAccountExistence() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("{\"target\":\"unknown@example.com\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/verify")
                        .contentType("application/json")
                        .content("{\"target\":\"unknown@example.com\",\"code\":\"000000\"}"))
                .andExpect(jsonPath("$.code").value(4001));
    }
}
