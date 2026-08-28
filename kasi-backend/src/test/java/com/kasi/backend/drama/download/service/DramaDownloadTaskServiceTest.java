package com.kasi.backend.drama.download.service;

import com.kasi.backend.drama.download.dto.CreateDramaDownloadTaskDTO;
import com.kasi.backend.drama.download.entity.DramaDownloadTask;
import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import com.kasi.backend.drama.download.mapper.DramaDownloadTaskMapper;
import com.kasi.backend.drama.download.service.impl.DramaDownloadTaskServiceImpl;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaContentResourceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaDownloadTaskServiceTest {
    @Mock DramaDownloadTaskMapper taskMapper;
    @Mock UserPromotionDramaService dramaService;
    @Mock TaskExecutor taskExecutor;
    @Mock DramaMediaDownloader mediaDownloader;
    @TempDir Path tempDir;

    private DramaDownloadTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DramaDownloadTaskServiceImpl(
                taskMapper, dramaService, taskExecutor, mediaDownloader,
                tempDir.toString(), "ffmpeg", 100, 24,
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("创建下载任务后进入排队状态并异步执行")
    void createEnqueuesPendingTask() {
        when(dramaService.getFreeContent(7L)).thenReturn(List.of(resource(101L, true)));
        doAnswer(invocation -> {
            invocation.getArgument(0, DramaDownloadTask.class).setId(9L);
            return 1;
        }).when(taskMapper).insert(any(DramaDownloadTask.class));

        CreateDramaDownloadTaskDTO request = new CreateDramaDownloadTaskDTO();
        request.setContentIds(List.of(101L));

        var result = service.create(3L, 7L, request);

        assertThat(result.getTaskId()).isEqualTo(9L);
        assertThat(result.getStatus()).isEqualTo(DramaDownloadTaskStatus.PENDING);
        assertThat(result.getTotalCount()).isEqualTo(1);
        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("下载源失败时任务标记为失败并保存脱敏错误")
    void processMarksTaskFailedWhenSourceDownloadFails() throws Exception {
        DramaDownloadTask task = new DramaDownloadTask();
        task.setId(9L);
        task.setUserId(3L);
        task.setDramaId(7L);
        task.setContentIdsJson("[101]");
        task.setTotalCount(1);
        task.setStatus(DramaDownloadTaskStatus.PENDING);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(dramaService.getFreeContent(7L)).thenReturn(List.of(resource(101L, true)));
        doThrow(new java.io.IOException("remote secret https://secret.example/key"))
                .when(mediaDownloader).download(eq("https://cdn.example/episode.m3u8"), any(Path.class), eq("ffmpeg"));

        service.process(9L);

        verify(mediaDownloader, times(3))
                .download(eq("https://cdn.example/episode.m3u8"), any(Path.class), eq("ffmpeg"));
        verify(taskMapper).markFailed(eq(9L), contains("素材下载失败"));
    }

    @Test
    @DisplayName("资源地址过期时强制刷新一次并使用新地址继续下载")
    void processRefreshesExpiredResourceOnce() throws Exception {
        DramaDownloadTask task = new DramaDownloadTask();
        task.setId(9L);
        task.setUserId(3L);
        task.setDramaId(7L);
        task.setContentIdsJson("[101]");
        task.setTotalCount(1);
        task.setStatus(DramaDownloadTaskStatus.PENDING);
        when(taskMapper.findById(9L)).thenReturn(task);
        when(dramaService.getFreeContent(7L)).thenReturn(List.of(resource(101L, true)));
        DramaContentResourceVO refreshed = resource(101L, true);
        refreshed.setDownloadUrl("https://cdn.example/episode-new.m3u8");
        when(dramaService.getFreeContent(7L, true)).thenReturn(List.of(refreshed));
        doThrow(new DramaMediaExpiredException())
                .when(mediaDownloader).download(eq("https://cdn.example/episode.m3u8"), any(Path.class), eq("ffmpeg"));
        doAnswer(invocation -> {
            Path output = invocation.getArgument(1, Path.class).resolveSibling("episode-1.mp4");
            java.nio.file.Files.writeString(output, "video");
            return output;
        }).when(mediaDownloader).download(eq("https://cdn.example/episode-new.m3u8"), any(Path.class), eq("ffmpeg"));

        service.process(9L);

        verify(dramaService).getFreeContent(7L, true);
        verify(taskMapper).markSuccess(eq(9L), anyString(), anyString());
    }

    private DramaContentResourceVO resource(Long id, boolean free) {
        return DramaContentResourceVO.builder().id(id).sequenceNo(1).title("第1集")
                .free(free).playUrl("https://cdn.example/episode.m3u8")
                .downloadUrl("https://cdn.example/episode.m3u8").build();
    }
}
