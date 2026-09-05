package com.kasi.backend.drama.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import com.kasi.backend.drama.enums.SyncRecordStatus;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.drama.service.DramaContentSyncService;
import com.kasi.backend.drama.service.DramaSyncRecordQueryService;
import com.kasi.backend.drama.vo.DramaContentSyncBatchVO;
import com.kasi.backend.drama.vo.DramaContentSyncTaskVO;
import com.kasi.backend.drama.vo.DramaSyncRecordVO;
import com.kasi.backend.drama.vo.DramaSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaContentSyncRecordDetailVO;
import com.kasi.backend.drama.vo.DramaSyncTaskVO;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
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
    @MockitoBean
    private DramaContentSyncService contentSyncService;
    @MockitoBean
    private DramaSyncRecordQueryService recordQueryService;

    private Long providerId;
    private Long dramaId;

    @BeforeEach
    void prepareCatalog() {
        providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?, 'GoodShort', 'USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,title_zh,description,cover_url,label_names,category_name,language,remote_rank,novel_type,novel_sub_type,remote_created_at,remote_updated_at,remote_show_status,local_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                connectionId, "book-1", "Time Story", "时间故事", "Intro", "https://img/1", "[\"爱情\"]", "爱情", "ENGLISH", 3, "ORIGINAL", 1,
                java.sql.Timestamp.valueOf("2025-08-27 11:26:18"), java.sql.Timestamp.valueOf("2025-08-28 11:26:18"), "ONLINE", "DRAFT");
        dramaId = jdbcTemplate.queryForObject("SELECT id FROM provider_drama WHERE external_drama_id='book-1'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_drama_content (drama_id,sequence_no,title,is_free) VALUES (?,?,?,?)",
                dramaId, 1, "Episode 1", 1);
        DramaSyncTaskVO task = new DramaSyncTaskVO(41L, DramaSyncType.FULL, "ENGLISH",
                DramaSyncStatus.REQUESTED, 1, null, 10, 9, 4, 5, 1, 0, null, null, null);
        when(syncService.requestSync(eq(providerId), eq(DramaSyncType.FULL), anyList())).thenReturn(List.of(task));
        when(syncService.getStatuses(providerId)).thenReturn(List.of(task));
        DramaContentSyncTaskVO contentTask = new DramaContentSyncTaskVO(
                51L, dramaId, DramaContentSyncStatus.REQUESTED,
                java.time.LocalDateTime.of(2026, 8, 28, 12, 0),
                java.time.LocalDateTime.of(2026, 8, 28, 12, 0),
                0, 0, 0, 0, null, null);
        DramaContentSyncBatchVO batch = new DramaContentSyncBatchVO(1, 1, 0, 0, List.of(contentTask));
        when(contentSyncService.request(dramaId)).thenReturn(contentTask);
        when(contentSyncService.requestBatch(anyList())).thenReturn(batch);
        when(contentSyncService.requestAll(eq(providerId), eq("ENGLISH"), eq(true))).thenReturn(batch);
        when(contentSyncService.getStatus(dramaId)).thenReturn(contentTask);
        DramaSyncRecordVO record = new DramaSyncRecordVO(
                "run-1", java.time.LocalDateTime.of(2026, 8, 29, 8, 0),
                SyncTriggerSource.MANUAL, DramaSyncTaskType.FULL,
                SyncRecordStatus.SUCCESS, 4, 5, 10);
        when(recordQueryService.listCatalog(providerId)).thenReturn(List.of(record));
        when(recordQueryService.listContent(providerId)).thenReturn(List.of(record));
        when(recordQueryService.catalogDetails(eq(providerId), eq("run-1"))).thenReturn(List.of(
                new DramaSyncRecordDetailVO(41L, "ENGLISH", DramaSyncType.FULL,
                        DramaSyncStatus.SUCCESS, 2, 1, 2, 3, null, null)));
        when(recordQueryService.contentDetails(eq(providerId), eq("run-1"))).thenReturn(List.of(
                new DramaContentSyncRecordDetailVO(51L, dramaId, "时间故事", "ENGLISH",
                        DramaContentSyncStatus.SUCCESS, 0, 1, 0, 1, null, null)));
    }

    @Test
    @DisplayName("普通管理员可查询详情触发同步查看状态和本地下架")
    void adminCanUseCatalogEndpoints() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/drama/catalog").param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].title").value("Time Story"))
                .andExpect(jsonPath("$.data.list[0].languageLabel").value("英语"))
                .andExpect(jsonPath("$.data.list[0].titleZh").value("时间故事"))
                .andExpect(jsonPath("$.data.list[0].labelNames[0]").value("爱情"))
                .andExpect(jsonPath("$.data.list[0].novelType").value("ORIGINAL"));
        mockMvc.perform(get("/api/admin/drama/catalog/{id}", dramaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.titleZh").value("时间故事"))
                .andExpect(jsonPath("$.data.remoteRank").value(3))
                .andExpect(jsonPath("$.data.contents[0].sequenceNo").value(1))
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
    @DisplayName("管理员可以读取后端实际生效的语言选项")
    void adminCanReadDramaLanguageOptions() throws Exception {
        mockMvc.perform(get("/api/drama/languages")
                        .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].value").value("ENGLISH"))
                .andExpect(jsonPath("$.data[0].label").value("英语"));
    }

    @Test
    @DisplayName("普通管理员可分别查询短剧和剧集聚合同步记录")
    void adminCanUseAggregatedSyncRecordEndpoints() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(get("/api/admin/drama/catalog/sync/records")
                        .param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskType").value("FULL"))
                .andExpect(jsonPath("$.data[0].totalProcessed").value(10));
        mockMvc.perform(get("/api/admin/drama/catalog/contents/sync/records")
                        .param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
        mockMvc.perform(get("/api/admin/drama/catalog/sync/records/run-1")
                        .param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].language").value("ENGLISH"));
        mockMvc.perform(get("/api/admin/drama/catalog/contents/sync/records/run-1")
                        .param("providerId", providerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dramaTitle").value("时间故事"));
    }

    @Test
    @DisplayName("普通管理员可以单部勾选全部同步免费剧集并查询任务状态")
    void adminCanUseContentSyncEndpoints() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/drama/catalog/{id}/contents/sync", dramaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dramaId").value(dramaId))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"));

        mockMvc.perform(post("/api/admin/drama/catalog/contents/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dramaIds\":[" + dramaId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queuedCount").value(1));

        mockMvc.perform(post("/api/admin/drama/catalog/contents/sync/all")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + providerId
                                + ",\"language\":\"ENGLISH\",\"missingOnly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(1));

        mockMvc.perform(get("/api/admin/drama/catalog/{id}/contents/sync/status", dramaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(51));
    }

    @Test
    @DisplayName("匿名和推广用户不能手动同步免费剧集")
    void anonymousAndUserCannotRequestContentSync() throws Exception {
        mockMvc.perform(post("/api/admin/drama/catalog/{id}/contents/sync", dramaId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/drama/catalog/{id}/contents/sync", dramaId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
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
        mockMvc.perform(post("/api/admin/drama/catalog/contents/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "dramaIds", java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    }
}
