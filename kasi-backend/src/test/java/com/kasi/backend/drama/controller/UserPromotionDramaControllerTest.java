package com.kasi.backend.drama.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("用户推广短剧接口")
class UserPromotionDramaControllerTest extends BaseAuthTest {
    private Long providerId;

    @BeforeEach
    void prepareCatalog() {
        providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?, 'GoodShort', 'USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("UPDATE short_drama_connection SET media_root_domain='novelopen.com' WHERE id=?", connectionId);
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,description,language,drama_type,commission_scope,promotion_description,remote_updated_at,remote_show_status,local_status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                connectionId, "published", "Published Drama", "Drama introduction", "ENGLISH", "本土剧",
                "ORDER,AD", "1. 单个视频建议不超过17分钟\n2. 点击创建推广任务获取", java.sql.Timestamp.valueOf("2026-08-23 20:24:46"), "1", "PUBLISHED");
        jdbcTemplate.update("UPDATE provider_drama SET title_zh=?,cover_url=?,label_names=?,category_name=?,remote_rank=?,novel_type=?,novel_sub_type=?,remote_created_at=? WHERE external_drama_id='published'",
                "中文剧名", "https://img/1", "[\"爱情\",\"霸总\"]", "爱情", 2, "TRANSLATION", 0,
                java.sql.Timestamp.valueOf("2026-08-20 20:24:46"));
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language,remote_created_at,remote_show_status,local_status) VALUES (?,?,?,?,?,?,?)",
                connectionId, "published-older", "Older Published Drama", "ENGLISH",
                java.sql.Timestamp.valueOf("2026-08-19 20:24:46"), "1", "PUBLISHED");
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language,remote_show_status,local_status) VALUES (?,?,?,?,?,?)",
                connectionId, "offline", "Offline Drama", "ENGLISH", "1", "OFFLINE");
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language,remote_show_status,local_status) VALUES (?,?,?,?,?,?)",
                connectionId, "remote-offline", "Remote Offline Drama", "ENGLISH", "0", "PUBLISHED");
    }

    @Test
    @DisplayName("推广用户只能看到已上架且远端有效的短剧")
    void userCanReadPublishedPromotionDramas() throws Exception {
        mockMvc.perform(get("/api/user/promotion/dramas")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].providerId").value(providerId))
                .andExpect(jsonPath("$.data.list[0].title").value("Published Drama"))
                .andExpect(jsonPath("$.data.list[1].title").value("Older Published Drama"))
                .andExpect(jsonPath("$.data.list[0].titleZh").value("中文剧名"))
                .andExpect(jsonPath("$.data.list[0].labelNames[1]").value("霸总"))
                .andExpect(jsonPath("$.data.list[0].novelSubType").value(0))
                .andExpect(jsonPath("$.data.list[0].description").value("Drama introduction"))
                .andExpect(jsonPath("$.data.list[0].commissionScopes[0]").value("ORDER"))
                .andExpect(jsonPath("$.data.list[0].commissionScopes[1]").value("AD"))
                .andExpect(jsonPath("$.data.list[0].promotionDescription").value("1. 单个视频建议不超过17分钟\n2. 点击创建推广任务获取"))
                .andExpect(jsonPath("$.data.list[0].remoteUpdatedAt").value("2026-08-23T20:24:46"));
    }

    @Test
    @DisplayName("匿名和管理员不能访问推广短剧接口")
    void promotionDramaEndpointEnforcesRoleBoundary() throws Exception {
        mockMvc.perform(get("/api/user/promotion/dramas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/promotion/dramas")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/user/promotion/dramas/1/free-content"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/promotion/dramas/1/free-content")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用户可以查看已上架短剧详情和剧集元数据")
    void userCanReadPublishedDramaDetail() throws Exception {
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='published'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_drama_content (drama_id,external_content_id,sequence_no,title,is_free,duration_seconds) VALUES (?,?,?,?,?,?)",
                dramaId, "episode-1", 1, "Episode 1", true, 90);

        mockMvc.perform(get("/api/user/promotion/dramas/" + dramaId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(dramaId))
                .andExpect(jsonPath("$.data.title").value("Published Drama"))
                .andExpect(jsonPath("$.data.contents[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data.contents[0].free").value(true));
    }

    @Test
    @DisplayName("用户不能查看未上架或甲方下架短剧详情")
    void userCannotReadUnavailableDramaDetail() throws Exception {
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='offline'", Long.class);
        mockMvc.perform(get("/api/user/promotion/dramas/" + dramaId)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(7010));
    }

    @Test
    @DisplayName("用户可以获取已上架短剧的免费剧集视频地址")
    void userCanReadFreeContentUrls() throws Exception {
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='published'", Long.class);
        jdbcTemplate.update("INSERT INTO provider_drama_content (drama_id,external_content_id,sequence_no,title,is_free,duration_seconds,content_url) VALUES (?,?,?,?,?,?,?)",
                dramaId, "episode-1", 1, "Chapter 1", true, 90,
                "https://v-koc.novelopen.com/episode-1.m3u8");
        jdbcTemplate.update("INSERT INTO provider_drama_content (drama_id,external_content_id,sequence_no,title,is_free,duration_seconds,content_url) VALUES (?,?,?,?,?,?,?)",
                dramaId, "episode-2", 2, "Chapter 2", true, 90,
                "https://unknown.example/episode-2.m3u8");

        mockMvc.perform(get("/api/user/promotion/dramas/" + dramaId + "/free-content")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data[0].playUrl").value("https://v-koc.novelopen.com/episode-1.m3u8"))
                .andExpect(jsonPath("$.data[0].downloadUrl").value("https://v-koc.novelopen.com/episode-1.m3u8"))
                .andExpect(jsonPath("$.data[1].playUrl").doesNotExist())
                .andExpect(jsonPath("$.data[1].downloadUrl").doesNotExist());

        mockMvc.perform(get("/api/user/promotion/dramas/" + dramaId + "/free-content")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].playUrl").value("https://v-koc.novelopen.com/episode-1.m3u8"));

        mockMvc.perform(get("/api/user/promotion/dramas/" + dramaId + "/free-content")
                        .param("refresh", "true")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].downloadUrl").value("https://v-koc.novelopen.com/episode-1.m3u8"));
    }
}
