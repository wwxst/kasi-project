package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.promotion.service.PromotionOrderUserService;
import com.kasi.backend.promotion.service.PromotionOrderAdminService;
import com.kasi.backend.promotion.vo.PromotionMonthlyCommissionVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderPageVO;
import com.kasi.backend.promotion.vo.UserPromotionOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserPromotionOrderControllerTest extends BaseAuthTest {
    @MockitoBean
    private PromotionOrderUserService userService;
    @MockitoBean
    private PromotionOrderAdminService adminService;

    @Test
    @DisplayName("推广用户可查询本人月度订单和净佣金")
    void userCanReadOwnMonthlyCommission() throws Exception {
        when(userService.getPage(anyLong(), any())).thenReturn(UserPromotionOrderPageVO.builder()
                .list(List.of(UserPromotionOrderVO.builder().externalOrderId("own-order")
                        .currency("USD").build()))
                .page(1).size(20).total(1).build());
        when(userService.getMonthly(anyLong(), any())).thenReturn(PromotionMonthlyCommissionVO.builder()
                .month("2025-07").paidOrderCount(2)
                .calculatedCommission(new BigDecimal("9.58"))
                .reversedCommission(new BigDecimal("4.79"))
                .netCommission(new BigDecimal("4.79")).build());

        String token = loginAsUser();
        mockMvc.perform(get("/api/user/promotion/orders?month=2025-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].externalOrderId").value("own-order"))
                .andExpect(jsonPath("$.data.list[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].orderAmount").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].commissionStatus").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].mediaAccountId").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].channelFeeRate").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].customParams").doesNotExist());
        mockMvc.perform(get("/api/user/promotion/orders/monthly?month=2025-07")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossOrderAmount").doesNotExist())
                .andExpect(jsonPath("$.data.netCommission").value(4.79));
    }

    @Test
    @DisplayName("用户订单接口拒绝管理员并提供本人CSV下载")
    void userOrderEndpointsEnforceRole() throws Exception {
        mockMvc.perform(get("/api/user/promotion/orders?month=2025-07")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
    }
}
