package com.kasi.backend.drama.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("平台分佣规则管理接口")
class ProviderCommissionRuleControllerTest extends BaseAuthTest {

    @Test
    @DisplayName("匿名访问规则列表返回未登录")
    void anonymousGetReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/drama/providers/1/commission-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("推广用户不能访问规则列表")
    void userGetReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/drama/providers/1/commission-rules")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("超级管理员创建规则返回百分比和状态")
    void superAdminCanCreateAndListRule() throws Exception {
        String token = loginAsAdmin();
        LocalDateTime from = now().plusMinutes(10);
        long providerId = providerId();
        mockMvc.perform(post(path(providerId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(from, null, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.channelFeeRate").value(30))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get(path(providerId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("普通管理员可以读取但不能执行四类写操作")
    void ordinaryAdminCannotWriteAnyRule() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        long providerId = providerId();
        mockMvc.perform(get(path(providerId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(now().plusMinutes(10), null, 30)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(itemPath(providerId, 1L)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(now().plusMinutes(10), null, 30)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(endPath(providerId, 1L)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(itemPath(providerId, 1L)).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("非法费率返回参数校验错误")
    void invalidRateReturnsValidationError() throws Exception {
        mockMvc.perform(post(path(providerId())).header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(now().plusMinutes(10), null, -1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("重叠规则返回时间重叠错误")
    void overlapReturnsConflictCode() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        LocalDateTime from = now().plusMinutes(10);
        String body = ruleJson(from, from.plusHours(1), 30);
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6013));
    }

    @Test
    @DisplayName("未来规则可以修改并删除")
    void futureRuleCanBeUpdatedAndDeleted() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        LocalDateTime from = now().plusMinutes(10);
        String response = mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(from, null, 30)))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        long ruleId = objectMapper.readTree(response).get("data").get("id").longValue();

        mockMvc.perform(put(itemPath(providerId, ruleId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(from.plusMinutes(1), null, 40)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.channelFeeRate").value(40));
        mockMvc.perform(delete(itemPath(providerId, ruleId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("当前规则只能提前结束且修改返回状态错误")
    void activeRuleCanEndButCannotUpdate() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        LocalDateTime from = now().minusMinutes(1);
        String response = mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(from, null, 30)))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        long ruleId = objectMapper.readTree(response).get("data").get("id").longValue();

        mockMvc.perform(put(itemPath(providerId, ruleId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(now().plusMinutes(10), null, 40)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6014));
        mockMvc.perform(patch(endPath(providerId, ruleId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveTo\":\"" + now().plusMinutes(30) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("不存在的平台和规则返回稳定业务错误")
    void missingProviderOrRuleReturnsBusinessCodes() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get(path(999L)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6001));
        long originalProviderId = providerId();
        mockMvc.perform(put(itemPath(originalProviderId, 999L)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(now().plusMinutes(10), null, 30)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6011));

        jdbcTemplate.update(
                "INSERT INTO short_drama_provider (provider_code, provider_name, status) VALUES (?, ?, ?)",
                "OTHER", "Other", 1);
        long otherProviderId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'OTHER'", Long.class);
        LocalDateTime future = now().plusMinutes(10);
        String response = mockMvc.perform(post(path(originalProviderId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(future, null, 30)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long originalRuleId = objectMapper.readTree(response).get("data").get("id").longValue();

        mockMvc.perform(put(itemPath(otherProviderId, originalRuleId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(future.plusMinutes(1), null, 40)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(6011));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private String path(long providerId) {
        return "/api/admin/drama/providers/" + providerId + "/commission-rules";
    }

    private String itemPath(long providerId, long ruleId) {
        return path(providerId) + "/" + ruleId;
    }

    private String endPath(long providerId, long ruleId) {
        return itemPath(providerId, ruleId) + "/end-time";
    }

    private long providerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }

    private String ruleJson(LocalDateTime from, LocalDateTime to, int channelRate) {
        String end = to == null ? "null" : "\"" + to + "\"";
        return "{\"channelFeeRate\":" + channelRate
                + ",\"principalFeeRate\":0,\"principalCommissionRate\":80"
                + ",\"downstreamFeeRate\":0,\"downstreamCommissionRate\":70"
                + ",\"effectiveFrom\":\"" + from + "\",\"effectiveTo\":" + end + "}";
    }
}
