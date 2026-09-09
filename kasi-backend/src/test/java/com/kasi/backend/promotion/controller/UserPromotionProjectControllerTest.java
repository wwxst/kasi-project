package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.service.PromotionProjectService;
import com.kasi.backend.promotion.vo.PromotionProjectCardVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPromotionProjectControllerTest extends BaseAuthTest {

    @MockitoBean
    private PromotionProjectService projectService;

    @Test
    void usersCanReadEnabledProjectCardsWithoutAdminFields() throws Exception {
        when(projectService.listEnabled()).thenReturn(List.of(PromotionProjectCardVO.builder()
                .id(1L)
                .name("项目A")
                .coverImageUrl("/uploads/promotion-projects/a.png")
                .projectDocumentUrl("https://example.com/doc")
                .sortOrder(10)
                .build()));

        mockMvc.perform(get("/api/user/promotion/projects")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("项目A"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(10))
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
    }

    @Test
    void unauthenticatedRequestsCannotReadProjectCards() throws Exception {
        mockMvc.perform(get("/api/user/promotion/projects"))
                .andExpect(status().isUnauthorized());
    }
}
