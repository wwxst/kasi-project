package com.kasi.backend.user;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户管理查询")
class UserManagementQueryTest extends BaseAuthTest {

    @Test
    @DisplayName("管理员按编号正序分页查询推广用户")
    void getPageReturnsUsersInAscendingOrder() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management")
                        .param("page", "1")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].userNo").value("KS000001"))
                .andExpect(jsonPath("$.data.list[0].password").doesNotExist());
    }

    @Test
    @DisplayName("管理员可以用邮箱搜索推广用户")
    void searchByEmailReturnsMatchingUser() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management")
                        .param("keyword", "test@example.com")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].userNo").value("KS000001"));
    }

    @Test
    @DisplayName("管理员查看推广用户详情")
    void getDetailReturnsUserWithoutPassword() throws Exception {
        String token = loginAsAdmin();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = 'KS000001'", Long.class);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("查看不存在推广用户返回3011")
    void getMissingDetailReturnsBusinessError() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/management/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(3011));
    }
}
