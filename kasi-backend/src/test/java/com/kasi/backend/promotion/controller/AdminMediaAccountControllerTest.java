package com.kasi.backend.promotion.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员媒体账号报备接口")
class AdminMediaAccountControllerTest extends BaseAuthTest {

    @Test
    @DisplayName("管理员可以下载指定文件名的 XLSX 报白明细")
    void adminCanExportMediaAccountFilingsAsXlsx() throws Exception {
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(get("/api/admin/promotion/media-accounts/export.xlsx")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=media-account-filings.xlsx"));
    }

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
    @DisplayName("报备状态筛选直接匹配五状态且无报白记录视为待提交")
    void filingStatusFilterUsesFivePersistedStatuses() throws Exception {
        long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, base_url, partner_id, api_key_ciphertext, currency, status) "
                        + "VALUES (?, 'GoodShort', 'https://goodshort.test', 'pid', 'cipher', 'USD', 1)", providerId);
        long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, providerId);

        insertMediaAccount("filter-without-filing");
        long notSubmittedId = insertMediaAccount("filter-not-submitted");
        insertFiling(connectionId, notSubmittedId, "NOT_SUBMITTED", "SUBMIT", null, null, null);
        long pendingId = insertMediaAccount("filter-pending");
        insertFiling(connectionId, pendingId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", "query failed");
        long approvedId = insertMediaAccount("filter-approved");
        insertFiling(connectionId, approvedId, "APPROVED", "NONE", "1", "CURRENT_TIMESTAMP", null);
        long rejectedId = insertMediaAccount("filter-rejected");
        insertFiling(connectionId, rejectedId, "REJECTED", "NONE", "2", "CURRENT_TIMESTAMP", "rejected");
        long submitFailedId = insertMediaAccount("filter-submit-failed");
        insertFiling(connectionId, submitFailedId, "SUBMIT_FAILED", "NONE", null, null, "report failed");

        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);
        assertFilingStatusFilter(adminToken, "NOT_SUBMITTED",
                "filter-not-submitted", "filter-without-filing");
        assertFilingStatusFilter(adminToken, "PENDING", "filter-pending");
        assertFilingStatusFilter(adminToken, "APPROVED", "filter-approved");
        assertFilingStatusFilter(adminToken, "REJECTED", "filter-rejected");
        assertFilingStatusFilter(adminToken, "SUBMIT_FAILED", "filter-submit-failed");
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
    @DisplayName("管理员列表和详情保留提交失败真实状态方式及错误")
    void adminSeesRealSubmitFailureDetails() throws Exception {
        long providerId = providerId();
        long connectionId = insertConnection();
        long accountId = insertMediaAccount("admin-real-submit-failure");
        insertFiling(connectionId, accountId, "SUBMIT_FAILED", "NONE", null, null, "report timeout");
        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'API', "
                        + "last_error_code = 'SUBMIT_OUTCOME_UNKNOWN' WHERE media_account_id = ?",
                accountId);

        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", "SUBMIT_FAILED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].providerId").value(providerId))
                .andExpect(jsonPath("$.data.list[0].filingMethod").value("API"))
                .andExpect(jsonPath("$.data.list[0].filingStatus").value("SUBMIT_FAILED"))
                .andExpect(jsonPath("$.data.list[0].filingLastErrorMessage").value("report timeout"));

        mockMvc.perform(get("/api/admin/promotion/media-accounts/{id}", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaAccount.filings[0].filingMethod").value("API"))
                .andExpect(jsonPath("$.data.mediaAccount.filings[0].status").value("SUBMIT_FAILED"))
                .andExpect(jsonPath("$.data.mediaAccount.filings[0].lastErrorCode")
                        .value("SUBMIT_OUTCOME_UNKNOWN"))
                .andExpect(jsonPath("$.data.mediaAccount.filings[0].lastErrorMessage")
                        .value("report timeout"));
    }

    @Test
    @DisplayName("平台方式和状态筛选必须由同一报白记录同时满足")
    void filingFiltersMustMatchTheSameFiling() throws Exception {
        long firstProviderId = providerId();
        long firstConnectionId = insertConnection();
        jdbcTemplate.update("INSERT INTO short_drama_provider (provider_code, provider_name, status) "
                + "VALUES ('SECOND_FILTER', 'Second Filter', 1)");
        long secondProviderId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'SECOND_FILTER'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, currency, status) VALUES (?, 'Second Filter', 'USD', 1)",
                secondProviderId);
        long secondConnectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, secondProviderId);

        long accountId = insertMediaAccount("same-filing-filter");
        insertFiling(firstConnectionId, accountId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);
        insertFiling(secondConnectionId, accountId, "REJECTED", "NONE", "2", "CURRENT_TIMESTAMP", null);
        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'MANUAL' WHERE connection_id = ?",
                secondConnectionId);
        insertMediaAccount("no-filing-filter");

        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("providerId", String.valueOf(firstProviderId))
                        .param("filingMethod", "MANUAL")
                        .param("filingStatus", "PENDING")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("providerId", String.valueOf(secondProviderId))
                        .param("filingMethod", "MANUAL")
                        .param("filingStatus", "REJECTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].externalAccountId").value("same-filing-filter"))
                .andExpect(jsonPath("$.data.list[0].providerId").value(secondProviderId))
                .andExpect(jsonPath("$.data.list[0].filingMethod").value("MANUAL"))
                .andExpect(jsonPath("$.data.list[0].filingStatus").value("REJECTED"));

        mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingMethod", "API")
                        .param("filingStatus", "NOT_SUBMITTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("管理员不能编辑媒体账号")
    void mediaAccountEditEndpointDoesNotExist() throws Exception {
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(put("/api/admin/promotion/media-accounts/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("管理员可处理人工报白状态且推广用户无权操作")
    void adminUpdatesManualFilingStatus() throws Exception {
        long providerId = providerId();
        long connectionId = insertConnection();
        long accountId = insertMediaAccount("manual-status");
        insertFiling(connectionId, accountId, "NOT_SUBMITTED", "NONE", null, null, "old error");
        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'MANUAL' WHERE media_account_id = ?",
                accountId);
        String path = "/api/admin/promotion/media-accounts/{id}/filings/{providerId}/status";
        String adminToken = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(patch(path, accountId, providerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM provider_media_filing WHERE media_account_id = ?", String.class, accountId))
                .isEqualTo("NOT_SUBMITTED");

        mockMvc.perform(patch(path, accountId, providerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForMap(
                "SELECT status, next_action, task_data_version, manual_updated_by, last_submitted_at, last_error_message "
                        + "FROM provider_media_filing WHERE media_account_id = ?", accountId))
                .containsEntry("STATUS", "APPROVED")
                .containsEntry("NEXT_ACTION", "NONE")
                .containsEntry("TASK_DATA_VERSION", 2)
                .containsEntry("MANUAL_UPDATED_BY", adminId("operator"))
                .containsEntry("LAST_SUBMITTED_AT", null)
                .containsEntry("LAST_ERROR_MESSAGE", null);

        mockMvc.perform(patch(path, accountId, providerId)
                        .header("Authorization", "Bearer " + loginAsUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员核实未知提交结果后才进入查询或开放重试")
    void adminResolvesUnknownSubmission() throws Exception {
        long providerId = providerId();
        long connectionId = insertConnection();
        long receivedId = insertMediaAccount("unknown-received");
        insertFiling(connectionId, receivedId, "SUBMIT_FAILED", "NONE", null, null, "timeout");
        jdbcTemplate.update("UPDATE provider_media_filing SET last_error_code = 'SUBMIT_OUTCOME_UNKNOWN', "
                + "last_submit_attempt_at = CURRENT_TIMESTAMP WHERE media_account_id = ?", receivedId);
        String path = "/api/admin/promotion/media-accounts/{id}/filings/{providerId}/submission-resolution";

        mockMvc.perform(post(path, receivedId, providerId)
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForMap(
                "SELECT next_action, submitted_data_version, last_submitted_at FROM provider_media_filing "
                        + "WHERE media_account_id = ?", receivedId))
                .containsEntry("NEXT_ACTION", "QUERY")
                .containsEntry("SUBMITTED_DATA_VERSION", null);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT last_submitted_at FROM provider_media_filing WHERE media_account_id = ?",
                java.time.LocalDateTime.class, receivedId)).isNotNull();

        long notReceivedId = insertMediaAccount("unknown-not-received");
        insertFiling(connectionId, notReceivedId, "SUBMIT_FAILED", "NONE", null, null, "timeout");
        jdbcTemplate.update("UPDATE provider_media_filing SET last_error_code = 'SUBMIT_OUTCOME_UNKNOWN', "
                + "last_submit_attempt_at = CURRENT_TIMESTAMP WHERE media_account_id = ?", notReceivedId);
        mockMvc.perform(post(path, notReceivedId, providerId)
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"NOT_RECEIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMIT_FAILED"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM provider_media_filing WHERE media_account_id = ?",
                String.class, notReceivedId)).isEqualTo("SUBMIT_CONFIRMED_NOT_RECEIVED");
    }

    @Test
    @DisplayName("管理员可以删除提交失败和甲方拒绝的媒体账号")
    void adminDeletesSubmissionFailureAndRemoteRejection() throws Exception {
        long connectionId = insertConnection();
        long submissionFailureId = insertMediaAccount("delete-submit-failed");
        insertFiling(connectionId, submissionFailureId, "SUBMIT_FAILED", "NONE", null, null, "report failed");
        long rejectedId = insertMediaAccount("delete-rejected");
        insertFiling(connectionId, rejectedId, "REJECTED", "NONE", "2", "CURRENT_TIMESTAMP", "rejected");
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        assertDeleteSucceeds(submissionFailureId, token);
        assertDeleteSucceeds(rejectedId, token);
    }

    @Test
    @DisplayName("管理员可以删除任意状态方式和租约中的媒体账号")
    void adminDeletesAccountsRegardlessOfFilingStateMethodOrLease() throws Exception {
        long connectionId = insertConnection();
        long submittingId = insertMediaAccount("delete-submitting");
        insertFiling(connectionId, submittingId, "NOT_SUBMITTED", "SUBMIT", null, null, "report failed");
        long auditingId = insertMediaAccount("delete-auditing");
        insertFiling(connectionId, auditingId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);
        long approvedId = insertMediaAccount("delete-approved");
        insertFiling(connectionId, approvedId, "APPROVED", "NONE", "1", "CURRENT_TIMESTAMP", null);
        long queryFailedId = insertMediaAccount("delete-query-failed");
        insertFiling(connectionId, queryFailedId, "PENDING", "NONE", null, "CURRENT_TIMESTAMP", "query failed");
        long manualId = insertMediaAccount("delete-manual");
        insertFiling(connectionId, manualId, "PENDING", "NONE", null, null, null);
        jdbcTemplate.update("UPDATE provider_media_filing SET filing_method = 'MANUAL' WHERE media_account_id = ?",
                manualId);
        long leasedId = insertMediaAccount("delete-active-lease");
        insertFiling(connectionId, leasedId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);
        jdbcTemplate.update("UPDATE provider_media_filing SET lease_owner = 'worker:lease', "
                + "lease_until = DATEADD('MINUTE', 5, CURRENT_TIMESTAMP) WHERE media_account_id = ?", leasedId);
        long noFilingId = insertMediaAccount("delete-without-filing");
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        for (long id : new long[]{submittingId, auditingId, approvedId, queryFailedId,
                manualId, leasedId, noFilingId}) {
            assertDeleteSucceeds(id, token);
        }
    }

    @Test
    @DisplayName("多平台报白账号删除时只删除选中账号及其全部报白")
    void deletingMultiFilingAccountOnlyDeletesTheSelectedAccount() throws Exception {
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
        long retainedAccountId = insertMediaAccount("delete-retained");
        insertFiling(firstConnectionId, mediaAccountId, "SUBMIT_FAILED", "NONE", null, null, "report failed");
        insertFiling(secondConnectionId, mediaAccountId, "PENDING", "QUERY", "0", "CURRENT_TIMESTAMP", null);
        insertFiling(firstConnectionId, retainedAccountId, "APPROVED", "NONE", "1", "CURRENT_TIMESTAMP", null);

        assertDeleteSucceeds(mediaAccountId, loginAsAdmin("operator", ADMIN_PASSWORD));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM promotion_media_account WHERE id = ?",
                Integer.class, retainedAccountId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_media_filing WHERE media_account_id = ?",
                Integer.class, retainedAccountId)).isEqualTo(1);
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

    private long providerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }

    private long adminId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_admin_user WHERE username = ?", Long.class, username);
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

    private void assertFilingStatusFilter(String token, String filingStatus,
                                          String... expectedExternalAccountIds) throws Exception {
        ResultActions result = mockMvc.perform(get("/api/admin/promotion/media-accounts")
                        .param("filingStatus", filingStatus)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(expectedExternalAccountIds.length))
                .andExpect(jsonPath("$.data.list.length()").value(expectedExternalAccountIds.length));
        for (int index = 0; index < expectedExternalAccountIds.length; index++) {
            result.andExpect(jsonPath("$.data.list[" + index + "].externalAccountId")
                    .value(expectedExternalAccountIds[index]));
        }
    }
}
