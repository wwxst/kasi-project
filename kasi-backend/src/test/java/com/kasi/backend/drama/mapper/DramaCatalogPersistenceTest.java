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
    @DisplayName("鐭墽鍜屽墽闆嗘敮鎸佸箓绛夋洿鏂颁笖淇濇寔鏈湴涓婁笅鏋朵笉鍙樁")
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
        assertThat(stored.getLocalStatus()).isEqualTo(DramaLocalStatus.PUBLISHED);

        ProviderDramaContent content = new ProviderDramaContent();
        content.setDramaId(id); content.setExternalContentId("ep-1"); content.setSequenceNo(1);
        content.setTitle("Episode 1"); content.setFree(true); content.setDurationSeconds(60);
        assertThat(dramaMapper.upsertContent(content)).isGreaterThanOrEqualTo(1);
        content.setTitle("Episode 1 updated");
        assertThat(dramaMapper.upsertContent(content)).isGreaterThanOrEqualTo(1);
        assertThat(dramaMapper.findContents(id)).singleElement().extracting(ProviderDramaContent::getTitle)
                .isEqualTo("Episode 1 updated");

        ProviderDramaContent contentWithoutExternalId = new ProviderDramaContent();
        contentWithoutExternalId.setDramaId(id); contentWithoutExternalId.setSequenceNo(2);
        contentWithoutExternalId.setTitle("Episode 2"); contentWithoutExternalId.setFree(false);
        assertThat(dramaMapper.upsertContent(contentWithoutExternalId)).isGreaterThanOrEqualTo(1);
        assertThat(dramaMapper.findContents(id)).hasSize(2);
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
        assertThat(checkpointMapper.requestRun(checkpoint.getId(), now)).isEqualTo(1);
        assertThat(checkpointMapper.claimLease(checkpoint.getId(), "worker-a", now, now.plusMinutes(2))).isEqualTo(1);
        assertThat(checkpointMapper.claimLease(checkpoint.getId(), "worker-b", now, now.plusMinutes(2))).isZero();
        assertThat(checkpointMapper.updateProgress(checkpoint.getId(), 2, 1700000000123L, 10, 9, 4, 5, 1, 0)).isEqualTo(1);
        assertThat(checkpointMapper.markSuccess(checkpoint.getId(), now, 2, 1700000000123L)).isEqualTo(1);
        ProviderSyncCheckpoint stored = checkpointMapper.findById(checkpoint.getId());
        assertThat(stored.getUpdateTime()).isEqualTo(1700000000123L);
        assertThat(stored.getStatus()).isEqualTo(DramaSyncStatus.SUCCESS);
        assertThat(stored.getTotalFetched()).isEqualTo(10);
        assertThat(stored.getTotalUpserted()).isEqualTo(9);
        assertThat(stored.getInsertedCount()).isEqualTo(4);
        assertThat(stored.getUpdatedCount()).isEqualTo(5);
        assertThat(stored.getSkippedCount()).isEqualTo(1);
        assertThat(stored.getErrorCount()).isZero();
    }

    private Long insertConnection() {
        Long providerId = jdbcTemplate.queryForObject("SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        jdbcTemplate.update("INSERT INTO short_drama_connection (provider_id,connection_name,currency) VALUES (?, 'GoodShort', 'USD')", providerId);
        return jdbcTemplate.queryForObject("SELECT id FROM short_drama_connection WHERE provider_id=?", Long.class, providerId);
    }

    private ProviderDrama drama(Long connectionId, String externalId) {
        ProviderDrama drama = new ProviderDrama(); drama.setConnectionId(connectionId); drama.setExternalDramaId(externalId);
        drama.setTitle("Original title"); drama.setOriginalTitle("Original title"); drama.setLanguage("ENGLISH");
        drama.setDramaType("SERIES"); drama.setRemoteShowStatus("ONLINE"); drama.setLastSeenAt(LocalDateTime.now());
        return drama;
    }
}
