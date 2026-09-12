package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.service.PromotionOrderAdminService;
import com.kasi.backend.promotion.service.PromotionOrderUserService;
import com.kasi.backend.promotion.vo.PromotionOrderPageVO;
import com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO;
import com.kasi.backend.promotion.vo.PromotionOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromotionOrderControllerTest extends BaseAuthTest {
    @MockitoBean
    private PromotionOrderAdminService adminService;
    @MockitoBean
    private PromotionOrderUserService userService;

    @Test
    @DisplayName("普通管理员可手动同步订单并获取处理数量")
    void adminCanSynchronizeOrders() throws Exception {
        when(adminService.sync(any())).thenReturn(PromotionOrderSyncResultVO.builder()
                .fetchedCount(3).insertedCount(2).updatedCount(1).unattributedCount(1).build());

        mockMvc.perform(post("/api/admin/promotion/orders/sync")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"startDate":"2025-07-01T00:00:00",
                                 "endDate":"2025-07-01T23:59:59"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fetchedCount").value(3))
                .andExpect(jsonPath("$.data.unattributedCount").value(1));
    }

    @Test
    @DisplayName("订单同步拒绝空参数和超过三十一天的窗口")
    void invalidSyncWindowReturnsValidationError() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(post("/api/admin/promotion/orders/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(post("/api/admin/promotion/orders/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerId":1,"startDate":"2025-07-01T00:00:00",
                                 "endDate":"2025-08-02T00:00:00"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("管理员订单接口拒绝推广用户并只提供 XLSX 下载")
    void orderEndpointsEnforceAdminRole() throws Exception {
        when(adminService.getPage(any())).thenReturn(PromotionOrderPageVO.builder()
                .list(List.of(PromotionOrderVO.builder().id(1L)
                        .trackingNo("tracking-admin-order").build()))
                .page(1).size(20).total(1).build());
        mockMvc.perform(get("/api/admin/promotion/orders")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].trackingNo").value("tracking-admin-order"));
        mockMvc.perform(get("/api/admin/promotion/orders")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/promotion/orders/export.xlsx")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=promotion-orders.xlsx"));
        mockMvc.perform(get("/api/admin/promotion/orders/export.csv")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
