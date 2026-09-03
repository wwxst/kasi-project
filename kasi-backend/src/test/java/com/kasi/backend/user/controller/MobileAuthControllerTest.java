package com.kasi.backend.user.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.kasi.backend.auth.service.TestVerificationCodeSender;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileAuthControllerTest extends BaseAuthTest {
    @Autowired
    private TestVerificationCodeSender sender;

    @Test
    @DisplayName("手机号验证码登录成功")
    void loginWithCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/login/code")
                        .contentType("application/json").content("{\"target\":\"13800138000\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/login/code/verify")
                        .contentType("application/json")
                        .content("{\"target\":\"13800138000\",\"code\":\"" + sender.latestCode("13800138000") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("手机号验证码登录不接受邮箱")
    void codeLoginRejectsEmail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/login/code")
                        .contentType("application/json").content("{\"target\":\"test@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
}
