package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员媒体账号报备接口")
class AdminMediaAccountControllerTest extends BaseAuthTest {

    @Test
    @DisplayName("普通管理员可以查询媒体账号但推广用户不能访问")
    void adminCanQueryMediaAccounts() throws Exception {
        long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO short_drama_connection " +
                        "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                providerId, "测试接入", "https://goodshort.test", "pid", "cipher", "USD", 1);
        long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
        jdbcTemplate.update(
                "INSERT INTO promotion_media_account " +
                        "(user_id, media_type, external_account_id, account_name, account_link, status, data_version) " +
                        "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), ?, ?, ?, ?, ?, ?)",
                PRIMARY_USER_NO, "TIKTOK", "creator-1001", "TikTok 运营号",
                "https://www.tiktok.com/@creator-1001", 1, 1);
        long mediaAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = 'creator-1001'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO provider_media_filing " +
                        "(connection_id, media_account_id, status, task_data_version, next_action, next_action_at) " +
                        "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                connectionId, mediaAccountId, "PENDING", 1, "SUBMIT");

        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.list[0].accountName").value("TikTok 运营号"))
                .andExpect(jsonPath("$.data.list[0].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaAccount.filings[0].nextActionAt").isNotEmpty());

        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("绠＄悊鍛樺彲缂栬緫濯掍綋璐﹀彿浣嗙敤鎴蜂笉鑳借闂")
    void adminCanUpdateMediaAccount() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO promotion_media_account " +
                        "(user_id, media_type, external_account_id, account_name, account_link, status, data_version) " +
                        "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), ?, ?, ?, ?, ?, ?)",
                PRIMARY_USER_NO, "TIKTOK", "creator-update", "旧名称",
                "https://www.tiktok.com/@creator-update", 1, 1);
        long mediaAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = 'creator-update'", Long.class);
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        String body = """
                {
                  "mediaType":"TIKTOK",
                  "externalAccountId":"creator-update",
                  "accountName":"新名称",
                  "accountLink":"https://www.tiktok.com/@creator-update",
                  "status":1
                }
                """;

        mockMvc.perform(put("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaAccount.accountName").value("新名称"));

        mockMvc.perform(put("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可以更新待处理报备的人工状态且重复更新失败")
    void adminCanUpdateManualFilingStatus() throws Exception {
        long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency, status) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'pid', 'cipher', 'USD', 1)", providerId);
        long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', 'manual-status', 1, 1)",
                PRIMARY_USER_NO);
        long mediaAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = 'manual-status'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, status, task_data_version, next_action, next_action_at) "
                        + "VALUES (?, ?, 'PENDING', 1, 'NONE', NULL)", connectionId, mediaAccountId);

        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(patch("/api/admin/promotion/media-accounts/{id}/filings/{providerId}/status",
                        mediaAccountId, providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(patch("/api/admin/promotion/media-accounts/{id}/filings/{providerId}/status",
                        mediaAccountId, providerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7008));

        mockMvc.perform(patch("/api/admin/promotion/media-accounts/{id}/filings/{providerId}/status",
                        mediaAccountId, providerId)
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isForbidden());
    }
}
