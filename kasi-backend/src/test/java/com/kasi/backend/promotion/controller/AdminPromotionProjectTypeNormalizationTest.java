package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.entity.PromotionProjectType;
import com.kasi.backend.promotion.mapper.PromotionProjectTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromotionProjectTypeNormalizationTest extends BaseAuthTest {
    @MockitoBean
    private PromotionProjectTypeMapper projectTypeMapper;

    @Test
    @DisplayName("MockMvc创建和更新项目类型时先归一化编码和名称")
    void createAndUpdateType_WithTrimmedMixedCaseInput_ReturnsNormalizedValues() throws Exception {
        when(projectTypeMapper.findByCode("CPA")).thenReturn(null);
        when(projectTypeMapper.insert(any())).thenAnswer(invocation -> {
            PromotionProjectType type = invocation.getArgument(0);
            type.setId(1L);
            return 1;
        });
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/promotion/project-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"code\":\" cpa \",\"name\":\" 按行动付费 \",\"status\":\"ENABLED\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("CPA"))
                .andExpect(jsonPath("$.data.name").value("按行动付费"));

        PromotionProjectType current = new PromotionProjectType();
        current.setId(1L);
        current.setCode("CPA");
        current.setName("按行动付费");
        current.setStatus(com.kasi.backend.promotion.enums.PromotionProjectStatus.ENABLED);
        current.setSortOrder(1);
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(current);
        when(projectTypeMapper.findByCode("CPM")).thenReturn(null);
        when(projectTypeMapper.update(any())).thenReturn(1);

        mockMvc.perform(put("/api/admin/promotion/project-types/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"code\":\" cpm \",\"name\":\" 按千次展示付费 \",\"status\":\"ENABLED\",\"sortOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("CPM"))
                .andExpect(jsonPath("$.data.name").value("按千次展示付费"));

        verify(projectTypeMapper).insert(any());
        verify(projectTypeMapper).update(any());
    }

    @Test
    @DisplayName("MockMvc创建项目类型使用非法编码时拒绝请求")
    void createType_WithInvalidCode_ReturnsValidationError() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/promotion/project-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"code\":\" 1cpa \",\"name\":\"非法\",\"status\":\"ENABLED\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
