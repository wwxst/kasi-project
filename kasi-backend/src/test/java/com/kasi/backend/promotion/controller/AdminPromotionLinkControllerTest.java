package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.service.PromotionLinkAdminService;
import com.kasi.backend.promotion.vo.AdminPromotionLinkPageVO;
import com.kasi.backend.promotion.vo.AdminPromotionLinkVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromotionLinkControllerTest extends BaseAuthTest {
    @MockitoBean
    private PromotionLinkAdminService adminService;

    @Test
    @DisplayName("管理员可以按任务查看推广转化，推广用户无权访问")
    void adminCanReadPromotionTasks() throws Exception {
        when(adminService.getPage(any())).thenReturn(AdminPromotionLinkPageVO.builder()
                .list(List.of(AdminPromotionLinkVO.builder().id(1L).userNo(PRIMARY_USER_NO)
                        .trackingNo("tracking-1").externalCode("code-1").clickCount(11L)
                        .orderCount(17L).build()))
                .page(1).size(20).total(1).build());

        mockMvc.perform(get("/api/admin/promotion/links?userNo={userNo}&providerId=1&externalCode=code-1&trackingNo=tracking-1",
                        PRIMARY_USER_NO)
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].userNo").value(PRIMARY_USER_NO))
                .andExpect(jsonPath("$.data.list[0].trackingNo").value("tracking-1"))
                .andExpect(jsonPath("$.data.list[0].externalCode").value("code-1"))
                .andExpect(jsonPath("$.data.list[0].clickCount").value(11))
                .andExpect(jsonPath("$.data.list[0].orderCount").value(17))
                .andExpect(jsonPath("$.data.list[0].orderAmount").doesNotExist());

        mockMvc.perform(get("/api/admin/promotion/links")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden());
    }
}
