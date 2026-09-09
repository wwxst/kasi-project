package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import com.kasi.backend.promotion.service.PromotionProjectService;
import com.kasi.backend.promotion.vo.PromotionProjectPageVO;
import com.kasi.backend.promotion.vo.PromotionProjectVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromotionProjectControllerTest extends BaseAuthTest {

    @MockitoBean
    private PromotionProjectService projectService;

    @Test
    void adminsCanListAndReadProjects() throws Exception {
        PromotionProjectVO project = project();
        when(projectService.getPage(any())).thenReturn(PromotionProjectPageVO.builder()
                .list(List.of(project)).page(1).size(20).total(1).build());
        when(projectService.getById(1L)).thenReturn(project);
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/promotion/projects?page=1&size=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("项目A"))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/admin/promotion/projects/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(10));
    }

    @Test
    void adminsCanCreateUpdateAndPhysicallyDeleteProjects() throws Exception {
        PromotionProjectVO project = project();
        when(projectService.create(any(), any())).thenReturn(project);
        when(projectService.update(eq(1L), any(), any())).thenReturn(project);
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        MockMultipartFile cover = new MockMultipartFile(
                "coverFile", "cover.png", "image/png", new byte[]{1});

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/admin/promotion/projects")
                        .file(cover)
                        .param("name", "项目A")
                        .param("projectDocumentUrl", "https://example.com/doc")
                        .param("status", "ENABLED")
                        .param("sortOrder", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl")
                        .value("/uploads/promotion-projects/a.png"));

        mockMvc.perform(MockMvcRequestBuilders.multipart(
                                HttpMethod.PUT, "/api/admin/promotion/projects/1")
                        .param("name", "项目A")
                        .param("projectDocumentUrl", "https://example.com/doc")
                        .param("status", "DISABLED")
                        .param("sortOrder", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/promotion/projects/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("项目删除成功"));
        verify(projectService).delete(1L);
    }

    @Test
    void promotionUsersCannotAccessAdminProjectCrud() throws Exception {
        mockMvc.perform(get("/api/admin/promotion/projects")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden());
    }

    private static PromotionProjectVO project() {
        return PromotionProjectVO.builder()
                .id(1L)
                .name("项目A")
                .coverImageUrl("/uploads/promotion-projects/a.png")
                .projectDocumentUrl("https://example.com/doc")
                .status(PromotionProjectStatus.ENABLED)
                .sortOrder(10)
                .build();
    }
}
