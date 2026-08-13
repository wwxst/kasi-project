package com.kasi.backend.security;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限隔离测试
 * <p>
 * 验证 ADMIN Token 和 USER Token 的正确隔离
 */
@DisplayName("权限隔离")
class SecurityPermissionTest extends BaseAuthTest {

    // ==================== USER Token 无法访问 ADMIN 接口 ====================

    @Test
    @DisplayName("USER Token访问 /api/admin/** 返回403")
    void userTokenCannotAccessAdminApi() throws Exception {
        String userToken = loginAsUser();

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ==================== ADMIN Token 无法访问 USER 接口 ====================

    @Test
    @DisplayName("ADMIN Token访问 /api/user/** 返回403")
    void adminTokenCannotAccessUserApi() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // ==================== 无 Token ====================

    @Test
    @DisplayName("无Token访问需要认证的接口返回401")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders
                        .get("/api/user/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 匿名接口 ====================

    @Test
    @DisplayName("匿名接口无需Token")
    void anonymousEndpointsAccessible() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"kasiadmin\",\"password\":\"kasi123456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/user/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"testuser\",\"password\":\"user123456\"}"))
                .andExpect(status().isOk());
    }

    // ==================== ADMIN Token 不可冒充 USER ====================

    @Test
    @DisplayName("ADMIN Token不能冒充USER访问用户专属接口")
    void adminCannotImpersonateUser() throws Exception {
        String adminToken = loginAsAdmin();

        // 尝试用ADMIN Token修改用户密码（即使知道用户API）
        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/user/auth/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"oldPassword":"user123456","newPassword":"hackedpass","confirmPassword":"hackedpass"}
                                """))
                .andExpect(status().isForbidden());
    }
}
