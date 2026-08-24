package com.kasi.backend.drama.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("平台默认分佣规则管理接口")
class ProviderCommissionRuleControllerTest extends BaseAuthTest {

    @Test
    @DisplayName("匿名访问规则列表返回未授权")
    void anonymousGetReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(path(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("推广用户不能访问规则列表")
    void userGetReturnsForbidden() throws Exception {
        mockMvc.perform(get(path(1L))
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("超级管理员创建并读取默认规则")
    void superAdminCanCreateAndListDefaultRule() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        mockMvc.perform(post(path(providerId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.channelFeeRate").value(30))
                .andExpect(jsonPath("$.data.effectiveFrom").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());

        mockMvc.perform(get(path(providerId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("普通管理员只读且不能新增或编辑")
    void ordinaryAdminCannotWrite() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        long providerId = providerId();
        mockMvc.perform(get(path(providerId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(30)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1003));
        mockMvc.perform(put(itemPath(providerId, 1L)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(40)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("重复创建默认规则返回已存在错误")
    void duplicateCreateReturnsExistingCode() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(30)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(40)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(6012));
    }

    @Test
    @DisplayName("编辑默认规则直接覆盖费率")
    void updateOverwritesDefaultRule() throws Exception {
        String token = loginAsAdmin();
        long providerId = providerId();
        String response = mockMvc.perform(post(path(providerId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(30)))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        long ruleId = objectMapper.readTree(response).get("data").get("id").longValue();

        mockMvc.perform(put(itemPath(providerId, ruleId)).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(40)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelFeeRate").value(40));
    }

    @Test
    @DisplayName("非法费率返回参数校验错误")
    void invalidRateReturnsValidationError() throws Exception {
        mockMvc.perform(post(path(providerId())).header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(ruleJson(-1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1006));
    }

    private long providerId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
    }

    private String path(long providerId) {
        return "/api/admin/drama/providers/" + providerId + "/commission-rules";
    }

    private String itemPath(long providerId, long ruleId) {
        return path(providerId) + "/" + ruleId;
    }

    private String ruleJson(int channelRate) {
        return "{\"channelFeeRate\":" + channelRate
                + ",\"principalFeeRate\":0,\"principalCommissionRate\":80"
                + ",\"downstreamFeeRate\":0,\"downstreamCommissionRate\":70}";
    }
}
