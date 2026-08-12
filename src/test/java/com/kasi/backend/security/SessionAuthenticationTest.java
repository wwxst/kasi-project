package com.kasi.backend.security;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.entity.AuthSession;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.security.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("会话认证过滤")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionAuthenticationTest extends BaseAuthTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SessionService sessionService;

    @Test
    @Order(1)
    @DisplayName("JWT对应账号禁用后返回401")
    void disabledAccountReturnsUnauthorized() throws Exception {
        String token = loginAsUser();
        jdbcTemplate.update("UPDATE promotion_user SET status = 0 WHERE username = 'testuser'");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("JWT对应账号软删除后返回401")
    void deletedAccountReturnsUnauthorized() throws Exception {
        String token = loginAsAdmin();
        jdbcTemplate.update("UPDATE sys_admin_user SET deleted_at = CURRENT_TIMESTAMP WHERE username = 'admin'");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(1)
    @DisplayName("有效USER会话访问ADMIN路由返回403")
    void validWrongRoleReturnsForbidden() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(1)
    @DisplayName("旧格式JWT缺少会话状态时返回401且不重建Redis")
    void tokenWithoutRedisStateReturnsUnauthorizedWithoutRebuilding() throws Exception {
        String token = tokenService.generateToken(
                1L, SubjectType.USER, "testuser", "orphan-jti", "orphan-version");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertFalse(
                redisTemplate.hasKey("auth:version:USER:1"));
    }

    @Test
    @Order(2)
    @DisplayName("受保护请求校验会话时Redis故障返回503")
    void redisFailureReturnsServiceUnavailable() throws Exception {
        String token = loginAsUser();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();

        try {
            connection.serverCommands().shutdown();

            mockMvc.perform(MockMvcRequestBuilders.get("/api/user/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(1007));
        } finally {
            connection.close();
        }
    }
}
