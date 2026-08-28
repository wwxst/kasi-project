package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.service.PromotionLinkService;
import com.kasi.backend.promotion.vo.PromotionLinkBatchVO;
import com.kasi.backend.promotion.vo.PromotionLinkPageVO;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户推广链接接口")
class UserPromotionLinkControllerTest extends BaseAuthTest {
    @MockitoBean
    private PromotionLinkService promotionLinkService;

    @BeforeEach
    void stubPromotionLinkService() {
        org.mockito.Mockito.when(promotionLinkService.getMine(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PromotionLinkPageVO.builder().list(java.util.List.of()).page(1).size(20).total(0).build());
        org.mockito.Mockito.when(promotionLinkService.createOrRetry(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PromotionLinkBatchVO.builder().batchNo("batch").links(java.util.List.of()).complete(true).build());
    }
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

    @Test
    @DisplayName("创建请求使用媒体平台列表并返回批次链接")
    void createUsesMediaTypesAndReturnsBatch() throws Exception {
        mockMvc.perform(post("/api/user/promotion/links")
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"dramaId":1,
                                 "mediaTypes":["TIKTOK"],
                                 "requestKey":"123e4567-e89b-12d3-a456-426614174000",
                                 "campaignName":"campaign"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchNo").exists())
                .andExpect(jsonPath("$.data.links").isArray());
    }

    @Test
    @DisplayName("创建请求拒绝媒体账号字段和未支持平台")
    void createRejectsAccountFieldAndUnsupportedMediaType() throws Exception {
        String token = loginAsUser();
        mockMvc.perform(post("/api/user/promotion/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"dramaId":1,"mediaTypes":["GOOGLE"],
                                 "mediaAccountId":8,
                                 "requestKey":"123e4567-e89b-12d3-a456-426614174001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
