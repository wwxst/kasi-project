package com.kasi.backend.sms.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("短信配置管理接口")
class SmsConfigAdminControllerTest extends BaseAuthTest {

    private static final String CONFIG_JSON = """
            {
              "accessKeyId":"ak-id",
              "accessKeySecret":"ak-secret",
              "signName":"卡司短剧",
              "registerTemplateCode":"SMS_1000001",
              "loginTemplateCode":"SMS_1000002",
              "resetPasswordTemplateCode":"SMS_1000003",
              "enabled":true,
              "emailEnabled":false
            }
            """;

    @Test
    @DisplayName("超级管理员可读写短信配置且响应不泄露密钥")
    void superAdminCanReadWriteWithoutSecrets() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/system/sms-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessKeyIdConfigured").value(true))
                .andExpect(jsonPath("$.data.accessKeySecretConfigured").value(true))
                .andExpect(jsonPath("$.data.accessKeyId").doesNotExist())
                .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/system/sms-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist())
                .andExpect(jsonPath("$.data.accessKeyIdConfigured").value(true));
    }

    @Test
    @DisplayName("普通管理员不能读写短信配置")
    void ordinaryAdminCannotReadOrWrite() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/system/sms-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/system/sms-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(CONFIG_JSON))
                .andExpect(status().isForbidden());
    }
}
