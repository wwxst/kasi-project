package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员管理写操作")
class AdminManagementMutationTest extends BaseAuthTest {

    @Test
    @DisplayName("超级管理员新增普通管理员并规范化联系方式")
    void createAdminStoresOrdinaryActiveAccount() throws Exception {
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = ?", Long.class, ADMIN_USERNAME);

        mockMvc.perform(post("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "finance1",
                                  "password": "OnlyLetters",
                                  "confirmPassword": "OnlyLetters",
                                  "realName": "财务管理员",
                                  "mobile": " 13600136000 ",
                                  "email": " Finance@Example.COM ",
                                  "avatarUrl": "https://example.com/avatar.png",
                                  "departmentId": 8,
                                  "remark": "财务部门"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("finance1"))
                .andExpect(jsonPath("$.data.email").value("finance@example.com"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_admin_user WHERE username = 'finance1'");
        assertThat(passwordEncoder.matches("OnlyLetters", (String) stored.get("password"))).isTrue();
        assertThat(stored.get("mobile")).isEqualTo("13600136000");
        assertThat(stored.get("email")).isEqualTo("finance@example.com");
        assertThat(((Number) stored.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) stored.get("is_super_admin")).intValue()).isZero();
        assertThat(((Number) stored.get("created_by")).longValue()).isEqualTo(operatorId);
        assertThat(((Number) stored.get("updated_by")).longValue()).isEqualTo(operatorId);
    }

    @Test
    @DisplayName("新增管理员两次密码不一致返回明确错误")
    void createAdminRejectsPasswordMismatch() throws Exception {
        mockMvc.perform(post("/api/admin/management")
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"finance1","password":"password1",
                                 "confirmPassword":"password2","realName":"财务管理员"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2011));
    }

    @Test
    @DisplayName("新增管理员分别识别账号手机号和邮箱重复")
    void createAdminRejectsDuplicateIdentifiers() throws Exception {
        String token = loginAsAdmin();
        jdbcTemplate.update("UPDATE sys_admin_user SET mobile = ?, email = ? WHERE username = 'operator'",
                "13500135000", "operator@example.com");

        assertDuplicate(token, "operator", null, null, 2007);
        assertDuplicate(token, "finance1", " 13500135000 ", null, 2008);
        assertDuplicate(token, "finance1", null, " OPERATOR@EXAMPLE.COM ", 2009);
    }

    @Test
    @DisplayName("新增管理员拒绝非法账号和非ASCII密码")
    void createAdminRejectsInvalidUsernameAndPassword() throws Exception {
        String token = loginAsAdmin();
        for (String username : new String[]{"finance_admin", "finance-admin", "财务", "finance admin"}) {
            mockMvc.perform(post("/api/admin/management")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", username,
                                    "password", "password1",
                                    "confirmPassword", "password1",
                                    "realName", "财务管理员"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1006));
        }

        mockMvc.perform(post("/api/admin/management")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"finance1","password":"密码password",
                                 "confirmPassword":"密码password","realName":"财务管理员"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private void assertDuplicate(String token, String username, String mobile, String email, int code)
            throws Exception {
        mockMvc.perform(post("/api/admin/management")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password1",
                                "confirmPassword", "password1",
                                "realName", "财务管理员",
                                "mobile", mobile == null ? "" : mobile,
                                "email", email == null ? "" : email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }
}
