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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("平台配置不再暴露独立报白模式接口")
    void filingModeEndpointsDoNotExist() throws Exception {
        Long providerId = providerId();
        String superToken = loginAsAdmin();
        configure(providerId, superToken);

        mockMvc.perform(get("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/filing-mode", providerId)
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingMode\":\"API\"}"))
                .andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.data.mediaRootDomain").value("novelopen.com"))
                .andExpect(jsonPath("$.data.apiFilingMediaTypes[0]").value("FACEBOOK"))
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
    @DisplayName("超级管理员可以保存无接入资料的停用配置")
    void superAdminCanSaveEmptyDisabledConnection() throws Exception {
        Long providerId = providerId();
        String token = loginAsAdmin();

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"apiFilingMediaTypes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.credentialConfigured").value(false));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT base_url, media_root_domain, partner_id, api_key_ciphertext, status "
                        + "FROM short_drama_connection WHERE provider_id = ?",
                providerId);
        assertThat(stored).containsEntry("STATUS", 0);
        assertThat(stored.get("BASE_URL")).isNull();
        assertThat(stored.get("MEDIA_ROOT_DOMAIN")).isNull();
        assertThat(stored.get("PARTNER_ID")).isNull();
        assertThat(stored.get("API_KEY_CIPHERTEXT")).isNull();
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
        for (String invalidRoot : new String[]{"https://novelopen.com", "*.novelopen.com",
                "novelopen.com/path", "evil..com", ""}) {
            assertValidationError(token, providerId, Map.of(
                    "mediaRootDomain", invalidRoot,
                    "connectionName", "GoodShort", "partnerId", "partner-1",
                    "baseUrl", "https://api.goodshort.test/creek", "apiKey", PLAINTEXT_KEY,
                    "currency", "USD", "status", 1));
        }
    }

    @Test
    @DisplayName("媒体复选框接受空集合并拒绝首次缺省、未知值和重复值")
    void apiFilingMediaTypesAreValidated() throws Exception {
        String token = loginAsAdmin();
        Long providerId = providerId();

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"apiFilingMediaTypes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.apiFilingMediaTypes").isEmpty());
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"apiFilingMediaTypes\":[\"UNKNOWN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"apiFilingMediaTypes\":[\"FACEBOOK\",\"FACEBOOK\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("媒体配置切换原子更新已有报白且批量保存不调用GoodShort")
    void mediaConfigurationSwitchesExistingFilingsAtomically() throws Exception {
        Long providerId = providerId();
        String token = loginAsAdmin();
        configure(providerId, token);
        long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
        long mediaAccountId = insertTikTokAccount("switch-existing");
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, filing_method, status, task_data_version, next_action) "
                        + "VALUES (?, ?, 'MANUAL', 'PENDING', 1, 'NONE')",
                connectionId, mediaAccountId);

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithMediaTypes("[\"FACEBOOK\",\"TIKTOK\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(jdbcTemplate.queryForMap("SELECT filing_method, status, next_action, task_data_version "
                        + "FROM provider_media_filing WHERE media_account_id = ?", mediaAccountId))
                .containsEntry("FILING_METHOD", "API")
                .containsEntry("STATUS", "PENDING")
                .containsEntry("NEXT_ACTION", "QUERY")
                .containsEntry("TASK_DATA_VERSION", 2);
        verify(goodShortAdapter, never()).submitAccountFiling(any(), any());

        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithMediaTypes("[\"FACEBOOK\"]")))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(jdbcTemplate.queryForMap("SELECT filing_method, next_action, task_data_version "
                        + "FROM provider_media_filing WHERE media_account_id = ?", mediaAccountId))
                .containsEntry("FILING_METHOD", "MANUAL")
                .containsEntry("NEXT_ACTION", "NONE")
                .containsEntry("TASK_DATA_VERSION", 3);

        jdbcTemplate.update("UPDATE provider_media_filing SET lease_owner = 'worker', "
                + "lease_until = TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP) WHERE media_account_id = ?", mediaAccountId);
        mockMvc.perform(put("/api/admin/drama/providers/{providerId}/connection", providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithMediaTypes("[\"FACEBOOK\",\"TIKTOK\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7018));
        assertThat(jdbcTemplate.queryForObject("SELECT api_filing_media_types FROM short_drama_connection "
                + "WHERE id = ?", String.class, connectionId)).isEqualTo("[\"FACEBOOK\"]");
        assertThat(jdbcTemplate.queryForObject("SELECT filing_method FROM provider_media_filing "
                + "WHERE media_account_id = ?", String.class, mediaAccountId)).isEqualTo("MANUAL");
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
                  "mediaRootDomain": "novelopen.com",
                  "baseUrl": "https://api.goodshort.test/creek",
                  "connectionName": "GoodShort默认账号",
                  "partnerId": "partner-1",
                  "apiKey": "goodshort-secret-key",
                  "currency": "USD",
                  "status": 1,
                  "apiFilingMediaTypes": ["FACEBOOK"]
                }
                """;
    }

    private String validRequestWithMediaTypes(String mediaTypes) {
        return validRequest().replace("[\"FACEBOOK\"]", mediaTypes);
    }

    private long insertTikTokAccount(String externalAccountId) {
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, account_name, account_link, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', ?, 'Creator', ?, 1, 1)",
                PRIMARY_USER_NO, externalAccountId, "https://tiktok.com/@" + externalAccountId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = ?", Long.class, externalAccountId);
    }

    private Long providerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }
}
