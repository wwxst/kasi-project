package com.kasi.backend.provider.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.goodshort.GoodShortAdapter;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("短剧平台接入管理接口")
class ProviderAdminControllerTest extends BaseAuthTest {

    private static final String PLAINTEXT_KEY = "goodshort-secret-key";

    @MockitoBean
    private GoodShortAdapter goodShortAdapter;

    @BeforeEach
    void stubAdapter() {
        when(goodShortAdapter.providerCode()).thenReturn("GOODSHORT");
        EnumSet<ProviderCapability> capabilities = EnumSet.allOf(ProviderCapability.class);
        capabilities.remove(ProviderCapability.TIKTOK_ANCHOR);
        when(goodShortAdapter.capabilities()).thenReturn(capabilities);
        when(goodShortAdapter.testConnection(any())).thenReturn(ProviderConnectionTestVO.builder()
                .reachable(true)
                .message("success")
                .testedAt(Instant.parse("2026-08-17T08:00:00Z"))
                .build());
    }

    @Test
    @DisplayName("未登录和推广用户不能查询平台配置")
    void anonymousAndUserCannotReadProviders() throws Exception {
        mockMvc.perform(get("/api/admin/drama/providers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/drama/providers")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("普通管理员可以查询但不能配置或测试连接")
    void ordinaryAdminCanReadButCannotMutateOrProbe() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        Long providerId = providerId();

        mockMvc.perform(get("/api/admin/drama/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(post("/api/admin/drama/providers/{providerId}/connection/test", providerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("瓒呯骇绠＄悊鍛樺彲淇敼鎶ョ櫧鏂瑰紡锛屾櫘閫氱鐞嗗憳鍙兘鏌ョ湅")
    void filingModeIsRestrictedToSuperAdmin() throws Exception {
        Long providerId = providerId();
        String superToken = loginAsAdmin();
        configure(providerId, superToken);

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingMode\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.providerName").value("GoodShort"))
                .andExpect(jsonPath("$.data.filingMode").value("MANUAL"));

        String ordinaryToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + ordinaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filingMode").value("MANUAL"));
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + ordinaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingMode\":\"API\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("鎶ョ櫧鏂瑰紡涓嶅厑璁哥┖鍊兼垨闈炴硶鍊?")
    void filingModeValidationReturnsValidationError() throws Exception {
        Long providerId = providerId();
        String token = loginAsAdmin();
        configure(providerId, token);

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
                mockMvc.perform(put("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingMode\":\"UNKNOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("超级管理员可以配置接入账号且响应不泄露密钥")
    void superAdminCanUpsertWithoutSecretExposure() throws Exception {
        Long providerId = providerId();
        String token = loginAsAdmin();

        String response = mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.goodshort.test/creek"))
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyCiphertext").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(PLAINTEXT_KEY).doesNotContain("ciphertext").doesNotContain("good****key");
        String stored = jdbcTemplate.queryForObject(
                "SELECT api_key_ciphertext FROM short_drama_connection WHERE provider_id = ?",
                String.class, providerId);
        assertThat(stored).startsWith("v1:").doesNotContain(PLAINTEXT_KEY);
    }

    @Test
    @DisplayName("超级管理员可以测试连接且查询结果不泄露密钥")
    void superAdminCanProbeAndReadWithoutSecretExposure() throws Exception {
        Long providerId = providerId();
        String token = loginAsAdmin();
        configure(providerId, token);

        mockMvc.perform(post("/api/admin/drama/providers/{providerId}/connection/test", providerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.message").value("success"));

        String response = mockMvc.perform(get("/api/admin/drama/providers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerCode").value("GOODSHORT"))
                .andExpect(jsonPath("$.data[0].capabilities").isArray())
                .andExpect(jsonPath("$.data[0].connection.credentialConfigured").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(PLAINTEXT_KEY).doesNotContain("apiKey").doesNotContain("ciphertext");
    }

    @Test
    @DisplayName("接入账号请求字段非法时返回统一校验错误")
    void invalidConnectionRequestsReturnValidationError() throws Exception {
        String token = loginAsAdmin();
        Long providerId = providerId();

        assertValidationError(token, providerId, Map.of(
                "connectionName", "GoodShort", "partnerId", "partner-1",
                "baseUrl", "https://api.goodshort.test/creek", "apiKey", PLAINTEXT_KEY,
                "currency", "usd", "status", 1));
        assertValidationError(token, providerId, Map.of(
                "partnerId", "partner-1", "baseUrl", "not-a-url",
                "apiKey", PLAINTEXT_KEY, "status", 1));
        assertValidationError(token, providerId, Map.of(
                "connectionName", "GoodShort", "partnerId", "partner-1",
                "baseUrl", "https://api.goodshort.test/creek", "apiKey", PLAINTEXT_KEY,
                "currency", "USD", "status", 2));
        assertValidationError(token, providerId, Map.of(
                "connectionName", "GoodShort", "partnerId", " ",
                "baseUrl", "https://api.goodshort.test/creek", "apiKey", PLAINTEXT_KEY,
                "currency", "USD", "status", 1));
        assertValidationError(token, providerId, Map.of(
                "connectionName", "x".repeat(65), "partnerId", "partner-1",
                "baseUrl", "https://api.goodshort.test/creek", "apiKey", PLAINTEXT_KEY,
                "currency", "USD", "status", 1));
    }

    private void assertValidationError(String token, Long providerId, Map<String, Object> request)
            throws Exception {
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private void configure(Long providerId, String token) throws Exception {
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(jsonPath("$.code").value(0));
    }

    private String validRequest() {
        return """
                {
                  "baseUrl": "https://api.goodshort.test/creek",
                  "connectionName": "GoodShort默认账号",
                  "partnerId": "partner-1",
                  "apiKey": "goodshort-secret-key",
                  "currency": "USD",
                  "status": 1
                }
                """;
    }

    private Long providerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }
}
