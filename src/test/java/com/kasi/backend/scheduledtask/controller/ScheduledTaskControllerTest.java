package com.kasi.backend.scheduledtask.controller;

import com.kasi.backend.BaseAuthTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("系统定时任务管理接口")
class ScheduledTaskControllerTest extends BaseAuthTest {

    private static final String BASE_PATH = "/api/admin/system/scheduled-tasks";
    private static final String TASK_CODE = "GOODSHORT_DRAMA_INCREMENTAL_SYNC";

    @Test
    @DisplayName("未登录和推广用户不能查询定时任务")
    void anonymousAndUserCannotReadTasks() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("普通管理员可以查询但不能修改定时任务")
    void ordinaryAdminCanReadButCannotUpdate() throws Exception {
        String token = loginAsAdmin("operator", ADMIN_PASSWORD);

        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].taskCode").value(TASK_CODE))
                .andExpect(jsonPath("$.data[0].title").value("GoodShort 短剧增量同步"))
                .andExpect(jsonPath("$.data[0].intervalMinutes").value(60))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].nextRunAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].leaseOwner").doesNotExist());

        mockMvc.perform(put(BASE_PATH + "/{taskCode}", TASK_CODE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    @DisplayName("超级管理员可以修改周期说明和启停状态")
    void superAdminCanUpdateTask() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{taskCode}", TASK_CODE)
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskCode").value(TASK_CODE))
                .andExpect(jsonPath("$.data.title").value("GoodShort 短剧增量同步"))
                .andExpect(jsonPath("$.data.description").value("每隔30分钟同步一次"))
                .andExpect(jsonPath("$.data.intervalMinutes").value(30))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.nextRunAt").doesNotExist())
                .andExpect(jsonPath("$.data.leaseUntil").doesNotExist());

        Integer enabled = jdbcTemplate.queryForObject(
                "SELECT enabled FROM system_scheduled_task WHERE task_code = ?",
                Integer.class, TASK_CODE);
        assertThat(enabled).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_run_at FROM system_scheduled_task WHERE task_code = ?",
                java.time.LocalDateTime.class, TASK_CODE)).isNull();
    }

    @Test
    @DisplayName("超级管理员可以保存结构化小时周期")
    void superAdminCanSaveStructuredCycle() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{taskCode}", TASK_CODE)
                        .header("Authorization", "Bearer " + loginAsAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cycleType": "INTERVAL_HOURS",
                                  "intervalValue": 2,
                                  "description": "每隔2小时同步一次",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cycleType").value("INTERVAL_HOURS"))
                .andExpect(jsonPath("$.data.intervalValue").value(2))
                .andExpect(jsonPath("$.data.intervalMinutes").value(120));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cycle_type FROM system_scheduled_task WHERE task_code = ?",
                String.class, TASK_CODE)).isEqualTo("INTERVAL_HOURS");
    }

    @Test
    @DisplayName("非法编辑字段返回统一参数校验错误")
    void invalidUpdatesReturnValidationError() throws Exception {
        String token = loginAsAdmin();

        assertValidationError(token, """
                {"intervalMinutes":4,"description":"说明","enabled":true}
                """);
        assertValidationError(token, """
                {"intervalMinutes":1441,"description":"说明","enabled":true}
                """);
        assertValidationError(token, """
                {"intervalMinutes":60,"description":" ","enabled":true}
                """);
        assertValidationError(token, """
                {"intervalMinutes":60,"description":"说明"}
                """);
        assertValidationError(token, objectMapper.writeValueAsString(java.util.Map.of(
                "intervalMinutes", 60,
                "description", "说".repeat(256),
                "enabled", true)));
    }

    private void assertValidationError(String token, String body) throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{taskCode}", TASK_CODE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private String validUpdate(boolean enabled) {
        return """
                {
                  "intervalMinutes": 30,
                  "description": "每隔30分钟同步一次",
                  "enabled": %s
                }
                """.formatted(enabled);
    }
}
