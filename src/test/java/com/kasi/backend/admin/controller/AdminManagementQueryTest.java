package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员管理查询")
class AdminManagementQueryTest extends BaseAuthTest {

    @Test
    @DisplayName("默认分页按ID正序且不返回密码")
    void getPageUsesDefaultsAndIdAscending() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list[0].username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.list[0].password").doesNotExist());
    }

    @Test
    @DisplayName("单一关键词搜索账号和真实姓名")
    void getPageSearchesKeyword() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .param("keyword", "operator")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value("operator"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .param("keyword", "运营")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("管理员详情存在与不存在返回明确结果")
    void getDetailHandlesExistingAndMissingAdmin() throws Exception {
        String token = loginAsAdmin();
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management/{id}", operatorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operator"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management/{id}", 999999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006));
    }

    @Test
    @DisplayName("非法分页参数返回校验错误")
    void getPageRejectsInvalidBounds() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/management")
                        .param("page", "0")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
