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
        jdbcTemplate.update("INSERT INTO provider_drama (connection_id,external_drama_id,title,language,remote_show_status,local_status) VALUES (?,?,?,?,?,?)",
                connectionId, "published", "Published Drama", "ENGLISH", "1", "PUBLISHED");
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
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].providerId").value(providerId))
                .andExpect(jsonPath("$.data.list[0].title").value("Published Drama"));
    }

    @Test
    @DisplayName("匿名和管理员不能访问推广短剧接口")
    void promotionDramaEndpointEnforcesRoleBoundary() throws Exception {
        mockMvc.perform(get("/api/user/promotion/dramas"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/promotion/dramas")
                        .header("Authorization", "Bearer " + loginAsAdmin()))
                .andExpect(status().isForbidden());
    }
}
