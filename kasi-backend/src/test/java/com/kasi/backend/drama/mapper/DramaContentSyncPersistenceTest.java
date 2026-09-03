package com.kasi.backend.drama.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.entity.DramaContentSyncTask;
import com.kasi.backend.drama.enums.DramaContentSyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("免费剧集同步任务持久层")
class DramaContentSyncPersistenceTest extends BaseAuthTest {

    @Autowired
    private DramaContentSyncTaskMapper taskMapper;
    @Autowired
    private ProviderDramaMapper dramaMapper;

    @Test
    @DisplayName("同一短剧任务唯一且到期任务只能由一个实例领取")
    void taskIsUniqueAndLeaseHasSingleOwner() {
        Long dramaId = insertDrama("content-task-book");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        DramaContentSyncTask task = requestedTask(dramaId, now);

        assertThat(taskMapper.insert(task)).isEqualTo(1);
        assertThat(taskMapper.findDueIds(now, 10)).containsExactly(task.getId());
        assertThat(taskMapper.claimLease(task.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(taskMapper.claimLease(task.getId(), "worker-b", now, now.plusMinutes(2))).isZero();

        assertThat(taskMapper.markSuccess(task.getId(), "worker-a", 2, 2, 0)).isEqualTo(1);
        DramaContentSyncTask stored = taskMapper.findByDramaId(dramaId);
        assertThat(stored.getStatus()).isEqualTo(DramaContentSyncStatus.SUCCESS);
        assertThat(stored.getTotalFetched()).isEqualTo(2);
        assertThat(stored.getInsertedCount()).isEqualTo(2);
        assertThat(stored.getUpdatedCount()).isZero();
        assertThat(stored.getLeaseOwner()).isNull();
    }

    @Test
    @DisplayName("暂时失败任务按下次执行时间重新排队且最终失败停止自动执行")
    void transientRetryAndFinalFailureArePersisted() {
        Long dramaId = insertDrama("content-retry-book");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        DramaContentSyncTask task = requestedTask(dramaId, now);
        taskMapper.insert(task);
        taskMapper.claimLease(task.getId(), "worker-a", now, now.plusMinutes(2));

        LocalDateTime retryAt = now.plusMinutes(5);
        assertThat(taskMapper.recordRetry(task.getId(), "worker-a", retryAt, 1,
                "REMOTE_TRANSIENT", "temporary failure")).isEqualTo(1);
        assertThat(taskMapper.findDueIds(now, 10)).isEmpty();
        assertThat(taskMapper.findDueIds(retryAt, 10)).containsExactly(task.getId());

        taskMapper.claimLease(task.getId(), "worker-b", retryAt, retryAt.plusMinutes(2));
        assertThat(taskMapper.markFailed(task.getId(), "worker-b", 2,
                "REMOTE_REJECTED", "rejected")).isEqualTo(1);
        DramaContentSyncTask failed = taskMapper.findByDramaId(dramaId);
        assertThat(failed.getStatus()).isEqualTo(DramaContentSyncStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(2);
        assertThat(failed.getLastErrorCode()).isEqualTo("REMOTE_REJECTED");
        assertThat(taskMapper.findDueIds(retryAt.plusDays(1), 10)).isEmpty();
    }

    @Test
    @DisplayName("仅缺失筛选会选中部分剧集地址为空的短剧")
    void missingOnlyIncludesDramaWithPartiallyMissingUrls() {
        Long dramaId = insertDrama("partially-missing-content-book");
        jdbcTemplate.update("""
                INSERT INTO provider_drama_content
                    (drama_id, sequence_no, title, is_free, content_url)
                VALUES (?, 1, 'Episode 1', 1, 'https://cdn.test/episode-1.m3u8'),
                       (?, 2, 'Episode 2', 1, NULL)
                """, dramaId, dramaId);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);

        assertThat(dramaMapper.findContentSyncCandidateIds(
                providerId, "ENGLISH", true, 0L, 100)).contains(dramaId);
    }

    private Long insertDrama(String externalId) {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) "
                + "VALUES (?, 'GoodShort', 'USD')", providerId);
        Long connectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
        jdbcTemplate.update("INSERT INTO provider_drama "
                        + "(connection_id,external_drama_id,title,language,remote_show_status,local_status) "
                        + "VALUES (?,?,?,?,?,?)",
                connectionId, externalId, "Drama", "ENGLISH", "1", "PUBLISHED");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM provider_drama WHERE external_drama_id=?", Long.class, externalId);
    }

    private DramaContentSyncTask requestedTask(Long dramaId, LocalDateTime now) {
        DramaContentSyncTask task = new DramaContentSyncTask();
        task.setDramaId(dramaId);
        task.setStatus(DramaContentSyncStatus.REQUESTED);
        task.setRequestedAt(now);
        task.setNextRunAt(now);
        task.setRetryCount(0);
        task.setTotalFetched(0);
        task.setInsertedCount(0);
        task.setUpdatedCount(0);
        return task;
    }
}
