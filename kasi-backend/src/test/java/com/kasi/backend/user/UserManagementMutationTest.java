package com.kasi.backend.user;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户管理写操作")
class UserManagementMutationTest extends BaseAuthTest {

    @Test
    @DisplayName("管理员新增推广用户并规范化联系人")
    void createUserStoresActiveAdminSourceAndBcryptPassword() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/management")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":" 13600136000 ","email":" New.User@Example.COM ",
                                 "nickname":" 新用户 ","realName":"张三","password":"newpass123!","confirmPassword":"newpass123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        var stored = jdbcTemplate.queryForMap("SELECT * FROM promotion_user WHERE mobile = '13600136000'");
        assertThat(stored.get("register_source")).isEqualTo("ADMIN");
        assertThat(passwordEncoder.matches("newpass123!", (String) stored.get("password"))).isTrue();
        assertThat(stored.get("user_no").toString()).matches("[1-9][0-9]{11}");
    }

    @Test
    @DisplayName("联系方式变更后所有旧会话失效")
    void updateContactInvalidatesAllSessions() throws Exception {
        String firstToken = loginAsUser("13800138000", USER_PASSWORD);
        String secondToken = loginAsUser("test@example.com", USER_PASSWORD);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM promotion_user WHERE mobile = '13800138000'", Long.class);
        String adminToken = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.put("/api/user/management/{id}", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"13900139001","email":"test@example.com","nickname":"修改后"}
                                """))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("禁用启用推广用户并旋转会话版本")
    void updateStatusControlsLoginAndInvalidatesToken() throws Exception {
        String userToken = loginAsUser();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = ?", Long.class, PRIMARY_USER_NO);
        String adminToken = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/user/management/{id}/status", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"13800138000\",\"password\":\"user123456\"}"))
                .andExpect(jsonPath("$.code").value(3002));
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/user/management/{id}/status", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("重置密码后旧凭据和旧会话失效")
    void resetPasswordInvalidatesOldCredentials() throws Exception {
        String userToken = loginAsUser();
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = ?", Long.class, PRIMARY_USER_NO);
        String adminToken = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.put("/api/user/management/{id}/password", id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"managedpass123!\",\"confirmPassword\":\"managedpass123!\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"13800138000\",\"password\":\"managedpass123!\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("物理删除后记录消失且联系方式可复用")
    void deletePhysicallyRemovesUserAndReusesContact() throws Exception {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE user_no = ?", Long.class, MOBILE_USER_NO);
        String adminToken = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/user/management/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM promotion_user WHERE id = ?", Integer.class, id)).isZero();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/management")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900139000\",\"nickname\":\"复用用户\",\"password\":\"password1\",\"confirmPassword\":\"password1\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("新增用户拒绝重复手机号和不一致密码")
    void createRejectsDuplicateMobileAndPasswordMismatch() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/management")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13800138000\",\"nickname\":\"重复用户\",\"password\":\"password1\",\"confirmPassword\":\"password1\"}"))
                .andExpect(jsonPath("$.code").value(3006));
        mockMvc.perform(MockMvcRequestBuilders.post("/api/user/management")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13600136000\",\"nickname\":\"新用户\",\"password\":\"password1\",\"confirmPassword\":\"password2\"}"))
                .andExpect(jsonPath("$.code").value(3013));
    }
}
