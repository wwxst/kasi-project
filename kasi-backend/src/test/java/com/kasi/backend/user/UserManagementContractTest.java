package com.kasi.backend.user;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户管理契约")
class UserManagementContractTest extends BaseAuthTest {

    @Test
    @DisplayName("未登录访问推广用户管理返回401")
    void managementWithoutTokenReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通管理员访问推广用户管理返回200")
    void ordinaryAdminCanAccessManagement() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("推广用户访问推广用户管理返回403")
    void promotionUserCannotAccessManagement() throws Exception {
        String token = loginAsUser();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
