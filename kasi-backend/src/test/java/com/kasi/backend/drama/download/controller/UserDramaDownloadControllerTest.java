package com.kasi.backend.drama.download.controller;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.download.dto.CreateDramaDownloadTaskDTO;
import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import com.kasi.backend.drama.download.service.DramaDownloadTaskService;
import com.kasi.backend.drama.download.vo.DramaDownloadTaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserDramaDownloadControllerTest extends BaseAuthTest {
    @MockitoBean
    private DramaDownloadTaskService service;

    @Test
    @DisplayName("用户可以创建并查询自己的短剧下载任务")
    void userCreatesAndReadsDownloadTask() throws Exception {
        when(service.create(anyLong(), eq(7L), any(CreateDramaDownloadTaskDTO.class)))
                .thenReturn(task(9L, DramaDownloadTaskStatus.PENDING));
        when(service.get(anyLong(), eq(9L))).thenReturn(task(9L, DramaDownloadTaskStatus.PENDING));

        String token = loginAsUser();
        mockMvc.perform(post("/api/user/promotion/dramas/7/downloads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"contentIds\":[101]}") )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(9))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/user/promotion/downloads/9")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(9));
    }

    @Test
    @DisplayName("成功任务以附件形式下载ZIP文件")
    void successfulTaskDownloadsZipFile() throws Exception {
        Path file = Files.createTempFile("drama-download-test-", ".zip");
        Files.writeString(file, "zip");
        when(service.getFile(anyLong(), eq(9L))).thenReturn(file);

        mockMvc.perform(get("/api/user/promotion/downloads/9/file")
                        .header("Authorization", "Bearer " + loginAsUser()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Type", "application/zip"));
        Files.deleteIfExists(file);
    }

    private DramaDownloadTaskVO task(Long id, DramaDownloadTaskStatus status) {
        return DramaDownloadTaskVO.builder().taskId(id).status(status).totalCount(1)
                .completedCount(0).expiresAt(LocalDateTime.now().plusHours(1)).build();
    }
}
