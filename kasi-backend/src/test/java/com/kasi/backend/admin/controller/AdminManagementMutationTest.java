package com.kasi.backend.admin.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    @DisplayName("超级管理员编辑普通管理员资料并使登录标识变更后的旧Token失效")
    void updateAdminInvalidatesOldTokenWhenIdentifierChanges() throws Exception {
        String operatorToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);

        mockMvc.perform(put("/api/admin/management/{id}", operatorId)
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator2","realName":"运营主管",
                                 "mobile":" 13900139001 ","email":" OPERATOR2@EXAMPLE.COM ",
                                 "avatarUrl":"https://example.com/operator.png",
                                 "departmentId":9,"remark":"运营部"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operator2"))
                .andExpect(jsonPath("$.data.email").value("operator2@example.com"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理接口不允许编辑唯一超级管理员")
    void updateAdminProtectsSuperAdmin() throws Exception {
        Long superAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = ?", Long.class, ADMIN_USERNAME);

        mockMvc.perform(put("/api/admin/management/{id}", superAdminId)
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"kasiadmin2","realName":"系统管理员",
                                 "mobile":null,"email":null,"avatarUrl":null,
                                 "departmentId":null,"remark":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2010));
    }

    @Test
    @DisplayName("禁用和重新启用普通管理员均旋转会话版本")
    void updateStatusInvalidatesOldTokenAndControlsLogin() throws Exception {
        String operatorToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        String superToken = loginAsAdmin();
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);

        mockMvc.perform(patch("/api/admin/management/{id}/status", operatorId)
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"operator\",\"password\":\"kasi123456\"}"))
                .andExpect(jsonPath("$.code").value(2002));

        mockMvc.perform(patch("/api/admin/management/{id}/status", operatorId)
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"operator\",\"password\":\"kasi123456\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("超级管理员重置普通管理员密码并使旧会话失效")
    void resetPasswordInvalidatesOldTokenAndCredentials() throws Exception {
        String operatorToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);

        mockMvc.perform(put("/api/admin/management/{id}/password", operatorId)
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123!\",\"confirmPassword\":\"newpass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"operator\",\"password\":\"kasi123456\"}"))
                .andExpect(jsonPath("$.code").value(2003));
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"operator\",\"password\":\"newpass123!\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("重置密码不一致和超级管理员目标均被拒绝")
    void statusAndPasswordProtectSuperAdminAndPasswordConfirmation() throws Exception {
        String token = loginAsAdmin();
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);
        Long superAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = ?", Long.class, ADMIN_USERNAME);

        mockMvc.perform(put("/api/admin/management/{id}/password", operatorId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\",\"confirmPassword\":\"newpass456\"}"))
                .andExpect(jsonPath("$.code").value(2011));
        mockMvc.perform(patch("/api/admin/management/{id}/status", superAdminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(2010));
        mockMvc.perform(put("/api/admin/management/{id}/password", superAdminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\",\"confirmPassword\":\"newpass123\"}"))
                .andExpect(jsonPath("$.code").value(2010));
    }

    @Test
    @DisplayName("物理删除普通管理员后旧会话失效且唯一字段可复用")
    void deleteAdminPhysicallyRemovesAccountAndAllowsReuse() throws Exception {
        jdbcTemplate.update("UPDATE sys_admin_user SET mobile = ?, email = ? WHERE username = 'operator'",
                "13500135000", "operator@example.com");
        String operatorToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = 'operator'", Long.class);
        String superToken = loginAsAdmin();

        mockMvc.perform(delete("/api/admin/management/{id}", operatorId)
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_admin_user WHERE id = ?", Integer.class, operatorId)).isZero();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/management")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"password1",
                                 "confirmPassword":"password1","realName":"新运营管理员",
                                 "mobile":"13500135000","email":"operator@example.com"}
                                """))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("物理删除拒绝超级管理员和不存在的目标")
    void deleteAdminProtectsSuperAdminAndMissingTarget() throws Exception {
        Long superAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_admin_user WHERE username = ?", Long.class, ADMIN_USERNAME);
        String token = loginAsAdmin();

        mockMvc.perform(delete("/api/admin/management/{id}", superAdminId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(2010));
        mockMvc.perform(delete("/api/admin/management/{id}", 999999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(2006));
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
