package com.kasi.backend.drama.download.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.download.entity.DramaDownloadTask;
import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DramaDownloadTaskPersistenceTest extends BaseAuthTest {
    @Autowired
    private DramaDownloadTaskMapper mapper;

    @Test
    @DisplayName("下载任务按用户持久化并更新进度与成功文件")
    void persistsOwnedTaskAndProgress() {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_user WHERE mobile='13800138000'", Long.class);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                + "(provider_id,connection_name,currency) VALUES (?,'download-test','USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                + "(connection_id,external_drama_id,title,language,remote_show_status,local_status) "
                + "VALUES (?,'download-book','Download Book','ENGLISH','1','PUBLISHED')", connectionId);
        Long dramaId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id='download-book'", Long.class);

        DramaDownloadTask task = new DramaDownloadTask();
        task.setUserId(userId);
        task.setDramaId(dramaId);
        task.setStatus(DramaDownloadTaskStatus.PENDING);
        task.setContentIdsJson("[101]");
        task.setTotalCount(1);
        task.setCompletedCount(0);
        task.setExpiresAt(LocalDateTime.now().plusHours(1));

        assertThat(mapper.insert(task)).isEqualTo(1);
        assertThat(task.getId()).isNotNull();
        assertThat(mapper.findByIdAndUserId(task.getId(), userId)).isNotNull();
        assertThat(mapper.findByIdAndUserId(task.getId(), userId + 1)).isNull();

        assertThat(mapper.markRunning(task.getId())).isEqualTo(1);
        assertThat(mapper.updateProgress(task.getId(), 1)).isEqualTo(1);
        assertThat(mapper.markSuccess(task.getId(), "E:/downloads/task.zip", "task.zip")).isEqualTo(1);
        DramaDownloadTask stored = mapper.findById(task.getId());
        assertThat(stored.getStatus()).isEqualTo(DramaDownloadTaskStatus.SUCCESS);
        assertThat(stored.getCompletedCount()).isEqualTo(1);
        assertThat(stored.getFileName()).isEqualTo("task.zip");
    }
}
