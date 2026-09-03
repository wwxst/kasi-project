package com.kasi.backend.drama.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("鐭墽鐩綍鎸佷箙灞?")
class DramaCatalogPersistenceTest extends BaseAuthTest {
    @Autowired private ProviderDramaMapper dramaMapper;
    @Autowired private ProviderSyncCheckpointMapper checkpointMapper;

    @Test
    @DisplayName("甲方下架时同步下架我方已上架短剧")
    void dramaAndContentUpsertIsIdempotent() {
        Long connectionId = insertConnection();
        ProviderDrama drama = drama(connectionId, "book-1");
        assertThat(dramaMapper.upsert(drama)).isGreaterThanOrEqualTo(1);
        Long id = dramaMapper.findByConnectionAndExternalId(connectionId, "book-1").getId();
        assertThat(dramaMapper.updateLocalStatus(id, DramaLocalStatus.PUBLISHED)).isEqualTo(1);

        drama.setTitle("Updated title");
        drama.setRemoteShowStatus("OFFLINE");
        assertThat(dramaMapper.upsert(drama)).isGreaterThanOrEqualTo(1);
        ProviderDrama stored = dramaMapper.findById(id);
        assertThat(stored.getTitle()).isEqualTo("Updated title");
        assertThat(stored.getTitleZh()).isEqualTo("中文标题");
        assertThat(stored.getLabelNames()).isEqualTo("[\"霸总\",\"爱情\"]");
        assertThat(stored.getCategoryName()).isEqualTo("爱情");
        assertThat(stored.getRemoteRank()).isEqualTo(3);
        assertThat(stored.getNovelType()).isEqualTo("ORIGINAL");
        assertThat(stored.getNovelSubType()).isEqualTo(1);
        assertThat(stored.getRemoteCreatedAt()).isNotNull();
        assertThat(stored.getLocalStatus()).isEqualTo(DramaLocalStatus.OFFLINE);

        ProviderDramaContent content = new ProviderDramaContent();
        content.setDramaId(id); content.setExternalContentId("ep-1"); content.setSequenceNo(1);
        content.setTitle("Episode 1"); content.setFree(true); content.setDurationSeconds(60);
        content.setContentUrl("https://cdn.test/episode-1.m3u8");
        assertThat(dramaMapper.upsertContent(content)).isGreaterThanOrEqualTo(1);
        content.setTitle("Episode 1 updated");
        content.setContentUrl("https://cdn.test/episode-1-updated.m3u8");
        assertThat(dramaMapper.upsertContent(content)).isGreaterThanOrEqualTo(1);
        assertThat(dramaMapper.findContents(id)).singleElement().satisfies(storedContent -> {
            assertThat(storedContent.getTitle()).isEqualTo("Episode 1 updated");
            assertThat(storedContent.getContentUrl()).isEqualTo("https://cdn.test/episode-1-updated.m3u8");
        });

        ProviderDramaContent contentWithoutExternalId = new ProviderDramaContent();
        contentWithoutExternalId.setDramaId(id); contentWithoutExternalId.setSequenceNo(2);
        contentWithoutExternalId.setTitle("Episode 2"); contentWithoutExternalId.setFree(false);
        assertThat(dramaMapper.upsertContent(contentWithoutExternalId)).isGreaterThanOrEqualTo(1);
        assertThat(dramaMapper.findContents(id)).hasSize(2);
    }

    @Test
    @DisplayName("新同步短剧按甲方状态默认上架或下架")
    void newDramaUsesRemoteAvailabilityAsInitialLocalStatus() {
        Long connectionId = insertConnection();
        ProviderDrama online = drama(connectionId, "online-book");
        online.setRemoteShowStatus("1");
        ProviderDrama offline = drama(connectionId, "offline-book");
        offline.setRemoteShowStatus("0");

        dramaMapper.upsert(online);
        dramaMapper.upsert(offline);

        assertThat(dramaMapper.findByConnectionAndExternalId(connectionId, "online-book").getLocalStatus())
                .isEqualTo(DramaLocalStatus.PUBLISHED);
        assertThat(dramaMapper.findByConnectionAndExternalId(connectionId, "offline-book").getLocalStatus())
                .isEqualTo(DramaLocalStatus.OFFLINE);
    }

    @Test
    @DisplayName("目录列表按甲方发布时间倒序返回")
    void pageOrdersByRemoteCreatedAtDescending() {
        Long connectionId = insertConnection();
        ProviderDrama older = drama(connectionId, "older-book");
        ProviderDrama newer = drama(connectionId, "newer-book");
        older.setRemoteCreatedAt(LocalDateTime.of(2026, 8, 20, 9, 0));
        newer.setRemoteCreatedAt(LocalDateTime.of(2026, 8, 21, 9, 0));
        dramaMapper.upsert(older);
        dramaMapper.upsert(newer);
        assertThat(dramaMapper.page(connectionId, null, null, null, null, 0, 2))
                .extracting(ProviderDrama::getExternalDramaId)
                .containsExactly("newer-book", "older-book");
    }

    @Test
    @DisplayName("甲方重新上架时不自动恢复我方下架状态")
    void remoteRepublishDoesNotOverrideManualOfflineStatus() {
        Long connectionId = insertConnection();
        ProviderDrama drama = drama(connectionId, "republished-book");
        drama.setRemoteShowStatus("0");
        dramaMapper.upsert(drama);
        Long id = dramaMapper.findByConnectionAndExternalId(connectionId, "republished-book").getId();
        assertThat(dramaMapper.updateLocalStatus(id, DramaLocalStatus.OFFLINE)).isEqualTo(1);

        drama.setRemoteShowStatus("1");
        dramaMapper.upsert(drama);

        assertThat(dramaMapper.findById(id).getLocalStatus()).isEqualTo(DramaLocalStatus.OFFLINE);
    }

    @Test
    @DisplayName("推广元数据可保存且目录同步upsert不会覆盖")
    void promotionMetadataSurvivesCatalogUpsert() {
        Long connectionId = insertConnection();
        ProviderDrama drama = drama(connectionId, "metadata-book");
        drama.setCommissionScope("ORDER,AD");
        drama.setPromotionDescription("1. 单个视频建议不超过17分钟");
        dramaMapper.upsert(drama);
        Long id = dramaMapper.findByConnectionAndExternalId(connectionId, "metadata-book").getId();

        assertThat(dramaMapper.updatePromotionMetadata(id, "AD", "2. 点击创建推广任务获取")).isEqualTo(1);
        drama.setTitle("Remote title updated");
        drama.setCommissionScope(null);
        drama.setPromotionDescription(null);
        dramaMapper.upsert(drama);

        ProviderDrama stored = dramaMapper.findById(id);
        assertThat(stored.getCommissionScope()).isEqualTo("AD");
        assertThat(stored.getPromotionDescription()).isEqualTo("2. 点击创建推广任务获取");
    }

    @Test
    @DisplayName("妫€鏌ョ偣鏀寔璇锋眰銆佺绾︺€佽繘搴︿笌鎴愬姛澶辫触鐘舵€")
    void checkpointLeaseProgressAndFailure() {
        Long connectionId = insertConnection();
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setConnectionId(connectionId); checkpoint.setSyncType(DramaSyncType.FULL);
        checkpoint.setLanguage("ENGLISH"); checkpoint.setStatus(DramaSyncStatus.IDLE);
        checkpoint.setPageNo(1); checkpoint.setPageSize(100);
        assertThat(checkpointMapper.insert(checkpoint)).isEqualTo(1);
        LocalDateTime now = LocalDateTime.now();
        assertThat(checkpointMapper.requestRun(checkpoint.getId(), now, true)).isEqualTo(1);
        assertThat(checkpointMapper.claimLease(checkpoint.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(checkpointMapper.claimLease(checkpoint.getId(), "worker-b", now, now.plusMinutes(2))).isZero();
        assertThat(checkpointMapper.updateProgress(checkpoint.getId(), "worker-a", 2, 1700000000123L, 10, 9, 4, 5, 1, 0)).isEqualTo(1);
        assertThat(checkpointMapper.markSuccess(checkpoint.getId(), "worker-a", now, 2, 1700000000123L)).isEqualTo(1);
        ProviderSyncCheckpoint stored = checkpointMapper.findById(checkpoint.getId());
        assertThat(stored.getUpdateTime()).isEqualTo(1700000000123L);
        assertThat(stored.getStatus()).isEqualTo(DramaSyncStatus.SUCCESS);
        assertThat(stored.getTotalFetched()).isEqualTo(10);
        assertThat(stored.getInsertedCount() + stored.getUpdatedCount()).isEqualTo(9);
        assertThat(stored.getInsertedCount()).isEqualTo(4);
        assertThat(stored.getUpdatedCount()).isEqualTo(5);
        assertThat(stored.getErrorCount()).isZero();
        assertThat(stored.getErrorCount()).isZero();
    }

    @Test
    @DisplayName("过期运行中租约可接管且旧持有者不能写回")
    void expiredRunningLeaseCanBeTakenOver() {
        Long connectionId = insertConnection();
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setConnectionId(connectionId); checkpoint.setSyncType(DramaSyncType.FULL);
        checkpoint.setLanguage("ENGLISH"); checkpoint.setStatus(DramaSyncStatus.IDLE);
        checkpoint.setPageNo(1); checkpoint.setPageSize(100);
        checkpointMapper.insert(checkpoint);
        LocalDateTime now = LocalDateTime.now();
        checkpointMapper.requestRun(checkpoint.getId(), now.minusMinutes(3), true);
        checkpointMapper.claimLease(checkpoint.getId(), "worker-old", now.minusMinutes(3), now.minusMinutes(1));

        assertThat(checkpointMapper.findDue(now, 10)).extracting(ProviderSyncCheckpoint::getId)
                .contains(checkpoint.getId());
        assertThat(checkpointMapper.claimLease(checkpoint.getId(), "worker-new", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(checkpointMapper.updateProgress(checkpoint.getId(), "worker-old", 2, null,
                1, 1, 1, 0, 0, 0)).isZero();
        assertThat(checkpointMapper.markFailure(checkpoint.getId(), "worker-old", now,
                "STALE", "stale worker")).isZero();
        assertThat(checkpointMapper.updateProgress(checkpoint.getId(), "worker-new", 2, null,
                1, 1, 1, 0, 0, 0)).isEqualTo(1);
        assertThat(checkpointMapper.markSuccess(checkpoint.getId(), "worker-new", now, 2, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("成功任务重跑重置进度而失败任务重试保留断点")
    void requestRunResetsSuccessAndResumesFailure() {
        Long connectionId = insertConnection();
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setConnectionId(connectionId); checkpoint.setSyncType(DramaSyncType.FULL);
        checkpoint.setLanguage("ENGLISH"); checkpoint.setStatus(DramaSyncStatus.IDLE);
        checkpoint.setPageNo(1); checkpoint.setPageSize(100);
        checkpointMapper.insert(checkpoint);
        LocalDateTime now = LocalDateTime.now();
        checkpointMapper.requestRun(checkpoint.getId(), now, true);
        checkpointMapper.claimLease(checkpoint.getId(), "worker-a", now, now.plusMinutes(2));
        checkpointMapper.updateProgress(checkpoint.getId(), "worker-a", 3, 1700000000123L,
                20, 20, 10, 10, 0, 0);
        checkpointMapper.markSuccess(checkpoint.getId(), "worker-a", now, 3, 1700000000123L);

        assertThat(checkpointMapper.requestRun(checkpoint.getId(), now.plusMinutes(1), true)).isEqualTo(1);
        ProviderSyncCheckpoint restarted = checkpointMapper.findById(checkpoint.getId());
        assertThat(restarted.getPageNo()).isEqualTo(1);
        assertThat(restarted.getUpdateTime()).isNull();
        assertThat(restarted.getTotalFetched()).isZero();

        checkpointMapper.claimLease(checkpoint.getId(), "worker-b", now.plusMinutes(1), now.plusMinutes(3));
        checkpointMapper.updateProgress(checkpoint.getId(), "worker-b", 2, null,
                5, 5, 5, 0, 0, 0);
        checkpointMapper.markFailure(checkpoint.getId(), "worker-b", now.plusMinutes(1),
                "REMOTE", "remote failed");
        assertThat(checkpointMapper.requestRun(checkpoint.getId(), now.plusMinutes(2), false)).isEqualTo(1);
        ProviderSyncCheckpoint resumed = checkpointMapper.findById(checkpoint.getId());
        assertThat(resumed.getPageNo()).isEqualTo(2);
        assertThat(resumed.getTotalFetched()).isEqualTo(5);
    }

    @Test
    @DisplayName("同一连接和语言的全量与增量任务不能同时领取租约")
    void crossTypeTasksAreMutuallyExclusive() {
        Long connectionId = insertConnection();
        ProviderSyncCheckpoint full = checkpoint(connectionId, DramaSyncType.FULL);
        ProviderSyncCheckpoint incremental = checkpoint(connectionId, DramaSyncType.INCREMENTAL);
        checkpointMapper.insert(full);
        checkpointMapper.insert(incremental);
        LocalDateTime now = LocalDateTime.now();
        checkpointMapper.requestRun(full.getId(), now, true);
        checkpointMapper.requestRun(incremental.getId(), now, true);

        assertThat(checkpointMapper.claimLease(full.getId(), "worker-full", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(checkpointMapper.claimLease(incremental.getId(), "worker-incremental", now,
                now.plusMinutes(2))).isZero();
    }

    private Long insertConnection() {
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?, 'GoodShort', 'USD')", providerId);
        return jdbcTemplate.queryForObject("SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
    }

    private ProviderDrama drama(Long connectionId, String externalId) {
        ProviderDrama drama = new ProviderDrama(); drama.setConnectionId(connectionId); drama.setExternalDramaId(externalId);
        drama.setTitle("Original title"); drama.setOriginalTitle("Original title"); drama.setLanguage("ENGLISH");
        drama.setTitleZh("中文标题"); drama.setLabelNames("[\"霸总\",\"爱情\"]"); drama.setCategoryName("爱情");
        drama.setRemoteRank(3); drama.setNovelType("ORIGINAL"); drama.setNovelSubType(1);
        drama.setRemoteCreatedAt(LocalDateTime.of(2025, 8, 27, 11, 26, 18));
        drama.setDramaType("SERIES"); drama.setRemoteShowStatus("ONLINE"); drama.setLastSeenAt(LocalDateTime.now());
        return drama;
    }

    private ProviderSyncCheckpoint checkpoint(Long connectionId, DramaSyncType type) {
        ProviderSyncCheckpoint checkpoint = new ProviderSyncCheckpoint();
        checkpoint.setConnectionId(connectionId); checkpoint.setSyncType(type);
        checkpoint.setLanguage("ENGLISH"); checkpoint.setStatus(DramaSyncStatus.IDLE);
        checkpoint.setPageNo(1); checkpoint.setPageSize(100);
        return checkpoint;
    }
}
