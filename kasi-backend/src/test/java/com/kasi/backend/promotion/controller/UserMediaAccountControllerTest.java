package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.crypto.CredentialCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private Long configureConnection() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency, status) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'partner-1', ?, 'USD', 1)",
                providerId, credentialCipher.encrypt("test-key"));
        return providerId;
    }
}
