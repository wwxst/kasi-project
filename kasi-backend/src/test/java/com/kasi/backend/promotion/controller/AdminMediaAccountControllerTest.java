package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @DisplayName("报备状态筛选按五种页面状态派生")
    void filingStatusFilterUsesDisplayStatus() throws Exception {
        long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency, status) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'pid', 'cipher', 'USD', 1)", providerId);
        long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, providerId);
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', 'display-filter', 1, 1)",
                PRIMARY_USER_NO);
        long mediaAccountId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = 'display-filter'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, status, next_action, last_error_message, task_data_version) "
                        + "VALUES (?, ?, 'PENDING', 'NONE', 'report failed', 1)", connectionId, mediaAccountId);

        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", "SUBMIT_FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].externalAccountId").value("display-filter"));

        long queryFailedId = insertMediaAccount("query-failed-filter");
        insertFiling(connectionId, queryFailedId, "FAILED", "NONE", null,
                "CURRENT_TIMESTAMP", "query failed");
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", "QUERY_FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].externalAccountId").value("query-failed-filter"));
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("报备状态筛选拒绝未知值并返回参数校验业务码")
    void filingStatusFilterRejectsUnknownValue() throws Exception {
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", "LEGACY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("管理员不能编辑媒体账号或人工修改报白状态")
    void adminMutationEndpointsDoNotExist() throws Exception {
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(put("/api/admin/promotion/media-accounts/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/admin/promotion/media-accounts/1/filings/1/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("管理员可以删除提交失败和甲方拒绝的媒体账号")
    void adminDeletesSubmissionFailureAndRemoteRejection() throws Exception {
        long connectionId = insertConnection();
        long submissionFailureId = insertMediaAccount("delete-submit-failed");
        insertFiling(connectionId, submissionFailureId, "PENDING", "NONE", null, null, "report failed");
        long rejectedId = insertMediaAccount("delete-rejected");
        insertFiling(connectionId, rejectedId, "FAILED", "NONE", "2", "CURRENT_TIMESTAMP", "rejected");
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        assertDeleteSucceeds(submissionFailureId, token);
        assertDeleteSucceeds(rejectedId, token);
    }

    @Test
    @DisplayName("管理员不能删除执行中、审核中、已加白或查询失败的媒体账号")
    void adminRejectsDeletionForProtectedStates() throws Exception {
        long connectionId = insertConnection();
        long submittingId = insertMediaAccount("delete-submitting");
        insertFiling(connectionId, submittingId, "PENDING", "SUBMIT", null, null, "report failed");
        long auditingId = insertMediaAccount("delete-auditing");
        insertFiling(connectionId, auditingId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);
        long approvedId = insertMediaAccount("delete-approved");
        insertFiling(connectionId, approvedId, "APPROVED", "NONE", "1", "CURRENT_TIMESTAMP", null);
        long queryFailedId = insertMediaAccount("delete-query-failed");
        insertFiling(connectionId, queryFailedId, "FAILED", "NONE", null, "CURRENT_TIMESTAMP", "query failed");
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        for (long id : new long[]{submittingId, auditingId, approvedId, queryFailedId}) {
            mockMvc.perform(delete("/api/admin/promotion/media-accounts/{id}", id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(7013));
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM promotion_media_account WHERE id = ?", Integer.class, id)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("任一平台报白不可删除时整条媒体账号不删除")
    void oneProtectedFilingPreventsWholeDeletion() throws Exception {
        long firstConnectionId = insertConnection();
        jdbcTemplate.update("INSERT INTO short_drama_provider (provider_code, provider_name, status) "
                + "VALUES ('SECOND', 'Second', 1)");
        long secondProviderId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'SECOND'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, currency, status) VALUES (?, 'Second', 'USD', 1)",
                secondProviderId);
        long secondConnectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, secondProviderId);
        long mediaAccountId = insertMediaAccount("delete-mixed");
        insertFiling(firstConnectionId, mediaAccountId, "PENDING", "NONE", null, null, "report failed");
        insertFiling(secondConnectionId, mediaAccountId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);

        mockMvc.perform(delete("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7013));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_media_filing WHERE media_account_id = ?",
                Integer.class, mediaAccountId)).isEqualTo(2);
    }

    private long insertConnection() {
        long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, currency, status) VALUES (?, 'GoodShort', 'USD', 1)",
                providerId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
    }

    private long insertMediaAccount(String externalAccountId) {
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, account_name, account_link, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', ?, 'Creator', ?, 1, 1)",
                PRIMARY_USER_NO, externalAccountId, "https://tiktok.com/@" + externalAccountId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = ?",
                Long.class, externalAccountId);
    }

    private void insertFiling(long connectionId, long mediaAccountId, String filingStatus, String nextAction,
                              String remoteStatus, String submittedAtSql, String errorMessage) {
        String submittedAt = submittedAtSql == null ? "NULL" : submittedAtSql;
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, status, task_data_version, next_action, remote_status, "
                        + "last_submitted_at, last_error_message) VALUES (?, ?, ?, 1, ?, ?, "
                        + submittedAt + ", ?)",
                connectionId, mediaAccountId, filingStatus, nextAction, remoteStatus, errorMessage);
    }

    private void assertDeleteSucceeds(long mediaAccountId, String token) throws Exception {
        mockMvc.perform(delete("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_media_filing WHERE media_account_id = ?",
                Integer.class, mediaAccountId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM promotion_media_account WHERE id = ?",
                Integer.class, mediaAccountId)).isZero();
    }
}
