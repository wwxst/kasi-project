package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户推广链接接口")
class UserPromotionLinkControllerTest extends BaseAuthTest {
    @Test
    @DisplayName("匿名和管理员不能访问推广链接")
    void userEndpointEnforcesRoleBoundary() throws Exception {
        mockMvc.perform(get("/api/user/promotion/links")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/promotion/links")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("推广用户可以查询自己的推广链接")
    void userCanReadOwnLinks() throws Exception {
        mockMvc.perform(get("/api/user/promotion/links")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("无效推广链接请求返回统一校验错误")
    void invalidCreateReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/user/promotion/links")
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
