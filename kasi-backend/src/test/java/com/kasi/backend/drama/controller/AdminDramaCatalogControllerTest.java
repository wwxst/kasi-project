package com.kasi.backend.drama.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.vo.DramaSyncTaskVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("管理员短剧目录接口")
class AdminDramaCatalogControllerTest extends BaseAuthTest {
    @MockitoBean
    private DramaCatalogSyncService syncService;

    private Long providerId;
    private Long dramaId;

    @BeforeEach
    void prepareCatalog() {
        providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?, 'GoodShort', 'USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language,remote_show_status,local_status) VALUES (?,?,?,?,?,?)",
                connectionId, "book-1", "Time Story", "ENGLISH", "ONLINE", "DRAFT");
        dramaId = jdbcTemplate.queryForObject("SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_drama_content (drama_id,sequence_no,title,is_free) VALUES (?,?,?,?)",
                dramaId, 1, "Episode 1", 1);
        DramaSyncTaskVO task = new DramaSyncTaskVO(41L, DramaSyncType.FULL, "ENGLISH",
                DramaSyncStatus.REQUESTED, 1, null, 10, 9, 4, 5, 1, 0, null, null, null);
        when(syncService.requestSync(eq(providerId), eq(DramaSyncType.FULL), anyList())).thenReturn(List.of(task));
        when(syncService.getStatuses(providerId)).thenReturn(List.of(task));
    }

    @Test
    @DisplayName("普通管理员可查询详情触发同步查看状态和本地下架")
    void adminCanUseCatalogEndpoints() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/drama/catalog").param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].title").value("Time Story"));
        mockMvc.perform(get("/api/admin/drama/catalog/{id}", dramaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contents[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data.connectionId").doesNotExist());
        mockMvc.perform(post("/api/admin/drama/catalog/sync")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + ",\"syncType\":\"FULL\",\"languages\":[\"ENGLISH\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("REQUESTED"));
        mockMvc.perform(get("/api/admin/drama/catalog/sync/status").param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].syncType").value("FULL"))
                .andExpect(jsonPath("$.data[0].insertedCount").value(4))
                .andExpect(jsonPath("$.data[0].updatedCount").value(5))
                .andExpect(jsonPath("$.data[0].skippedCount").value(1))
                .andExpect(jsonPath("$.data[0].errorCount").value(0));
        mockMvc.perform(patch("/api/admin/drama/catalog/{id}/status", dramaId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localStatus\":\"PUBLISHED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.localStatus").value("PUBLISHED"));
        mockMvc.perform(put("/api/admin/drama/catalog/{id}/promotion-metadata", dramaId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionScopes\":[\"ORDER\",\"AD\"],\"promotionDescription\":\"说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionScopes[0]").value("ORDER"))
                .andExpect(jsonPath("$.data.promotionDescription").value("说明"));
    }

    @Test
    @DisplayName("匿名和推广用户不能访问管理员目录")
    void anonymousAndUserCannotAccessCatalog() throws Exception {
        mockMvc.perform(get("/api/admin/drama/catalog")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/drama/catalog").header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("非法分页同步和上下架请求返回统一校验错误")
    void invalidRequestsReturnValidationError() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/drama/catalog").param("page", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(post("/api/admin/drama/catalog/sync")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(patch("/api/admin/drama/catalog/{id}/status", dramaId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
        mockMvc.perform(put("/api/admin/drama/catalog/{id}/promotion-metadata", dramaId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionScopes\":[\"UNKNOWN\"],\"promotionDescription\":\"说明\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    }
}
