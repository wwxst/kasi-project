package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.crypto.CredentialCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("推广用户媒体账号接口")
class UserMediaAccountControllerTest extends BaseAuthTest {

    @Autowired
    private CredentialCipher credentialCipher;

    @Test
    @DisplayName("匿名和管理员不能访问推广用户媒体账号接口")
    void userEndpointEnforcesRoleBoundary() throws Exception {
        mockMvc.perform(get("/api/user/promotion/media-accounts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/promotion/media-accounts")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("推广用户可以绑定媒体账号并建立报备")
    void userCanCreateMediaAccountFiling() throws Exception {
        Long providerId = configureConnection();

        mockMvc.perform(post("/api/user/promotion/media-accounts")
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"TIKTOK","externalAccountId":"creator-1",
                                 "accountName":"Creator One","accountLink":"https://tiktok.com/@creator-1",
                                 "providerId":%d}
                                """.formatted(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.externalAccountId").value("creator-1"))
                .andExpect(jsonPath("$.data.filings[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.filings[0].connectionId").doesNotExist());
    }

    @Test
    @DisplayName("未勾选媒体可在API凭据不可用时创建人工报备")
    void userCanCreateManualFilingWithoutApiRuntime() throws Exception {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, media_root_domain, partner_id, "
                        + "api_key_ciphertext, currency, status, api_filing_media_types) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'goodshort.test', 'partner-1', "
                        + "'not-decryptable', 'USD', 1, '[\"FACEBOOK\"]')",
                providerId);

        mockMvc.perform(post("/api/user/promotion/media-accounts")
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mediaType":"TIKTOK","externalAccountId":"creator-manual",
                                 "accountName":"Manual Creator","accountLink":"https://tiktok.com/@creator-manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.externalAccountId").value("creator-manual"))
                .andExpect(jsonPath("$.data.filings[0].filingMethod").value("MANUAL"))
                .andExpect(jsonPath("$.data.filings[0].status").value("PENDING"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_media_filing f "
                        + "JOIN promotion_media_account a ON a.id = f.media_account_id "
                        + "WHERE a.external_account_id = ? AND f.filing_method = 'MANUAL' "
                        + "AND f.status = 'PENDING' AND f.next_action = 'NONE' "
                        + "AND f.next_action_at IS NULL AND f.last_submit_attempt_at IS NULL",
                Integer.class, "creator-manual")).isEqualTo(1);
    }

    @Test
    @DisplayName("推广用户将提交失败视为审核中且看不到内部错误")
    void userSeesSubmitFailureAsPendingWithoutInternalError() throws Exception {
        configureConnection();
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection ORDER BY id DESC LIMIT 1", Long.class);
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, account_name, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', ?, 'Creator', 1, 1)",
                PRIMARY_USER_NO, "user-hidden-submit-failure");
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = ?",
                Long.class, "user-hidden-submit-failure");
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, filing_method, status, task_data_version, next_action, "
                        + "last_error_code, last_error_message) VALUES (?, ?, 'API', 'SUBMIT_FAILED', 1, 'NONE', ?, ?)",
                connectionId, accountId, "SUBMIT_OUTCOME_UNKNOWN", "report timeout");

        String token = loginAsUser();
        mockMvc.perform(get("/api/user/promotion/media-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].filings[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].filings[0].filingMethod").value("API"))
                .andExpect(jsonPath("$.data[0].filings[0].lastErrorCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].filings[0].lastErrorMessage").doesNotExist());

        mockMvc.perform(get("/api/user/promotion/media-accounts/{id}", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filings[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.filings[0].filingMethod").value("API"))
                .andExpect(jsonPath("$.data.filings[0].lastErrorCode").doesNotExist())
                .andExpect(jsonPath("$.data.filings[0].lastErrorMessage").doesNotExist());
    }

    @Test
    @DisplayName("推广用户不能修改、启停或重试媒体账号报白")
    void mutationAndRetryEndpointsDoNotExist() throws Exception {
        String token = loginAsUser();

        mockMvc.perform(put("/api/user/promotion/media-accounts/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/user/promotion/media-accounts/1/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/user/promotion/media-accounts/1/filings/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private Long configureConnection() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, media_root_domain, partner_id, api_key_ciphertext, currency, status) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'goodshort.test', 'partner-1', ?, 'USD', 1)",
                providerId, credentialCipher.encrypt("test-key"));
        return providerId;
    }
}
