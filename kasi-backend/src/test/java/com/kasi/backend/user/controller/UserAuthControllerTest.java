package com.kasi.backend.user.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.auth.service.TestVerificationCodeSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Base64;

import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(userNo).matches("[1-9][0-9]{11}");
        assertThat(userNo).doesNotStartWith("TMP-");
        org.junit.jupiter.api.Assertions.assertEquals("MOBILE", source);
        assertThat(nickname).matches("卡司用户[0-9]{5}");
    }

    @Test
    @DisplayName("邮箱注册统一转小写并记录EMAIL来源")
    void registerWithEmailNormalizesAccountAndSource() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/register/code")
                        .contentType("application/json")
                        .content("{\"target\":\" New.User@Example.COM \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

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
                .andExpect(jsonPath("$.data.user.id").doesNotExist())
                .andExpect(jsonPath("$.data.user.userNo").value(PRIMARY_USER_NO));
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
                .andExpect(jsonPath("$.data.user.userNo").value(PRIMARY_USER_NO));
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
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userNo").value(PRIMARY_USER_NO));
    }

    @Test
    @DisplayName("未登录获取用户信息返回401")
    void getCurrentUserWithoutToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("推广用户修改昵称和真实姓名后原Token继续有效")
    void updateOwnProfileKeepsTokenValid() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(MockMvcRequestBuilders.put("/api/user/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"nickname":" 新昵称 ","realName":" 张三 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.realName").value("张三"))
                .andExpect(jsonPath("$.data.mobile").value("13800138000"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.realName").value("张三"));
    }

    @Test
    @DisplayName("推广用户上传本人头像后原Token继续有效")
    void uploadOwnAvatarKeepsTokenValid() throws Exception {
        String token = loginAsUser();
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", pngBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/user/auth/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl", startsWith("/uploads/user-avatars/")));

        String avatarUrl = jdbcTemplate.queryForObject(
                "SELECT avatar_url FROM promotion_user WHERE user_no = ?", String.class, PRIMARY_USER_NO);
        assertThat(avatarUrl).startsWith("/uploads/user-avatars/");
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl));
    }

    @Test
    @DisplayName("推广用户上传伪图片时返回头像格式错误")
    void uploadOwnAvatarRejectsInvalidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/user/auth/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3015));
    }

    @Test
    @DisplayName("未登录不能修改推广用户资料或头像")
    void updateOwnProfileAndAvatarWithoutToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/user/auth/profile")
                        .contentType("application/json")
                        .content("{\"nickname\":\"新昵称\",\"realName\":\"张三\"}"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", pngBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/user/auth/avatar")
                        .file(file))
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
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/password/forgot/code")
                        .contentType("application/json")
                        .content("{\"target\":\"another-nonexistent@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
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

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
