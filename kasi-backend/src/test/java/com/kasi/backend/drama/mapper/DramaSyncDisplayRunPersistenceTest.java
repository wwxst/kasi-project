package com.kasi.backend.drama.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("同步展示运行持久层")
class DramaSyncDisplayRunPersistenceTest extends BaseAuthTest {
    @Autowired
    private DramaSyncDisplayRunMapper mapper;

    @Test
    @DisplayName("一次展示运行可关联多个底层任务且按领域读取")
    void displayRunStoresAndReadsTaskItems() {
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code='GOODSHORT'", Long.class);
        DramaSyncDisplayRun run = new DramaSyncDisplayRun();
        run.setId("run-catalog-1");
        run.setProviderId(providerId);
        run.setDomain(DramaSyncDomain.CATALOG);
        run.setTaskType(DramaSyncTaskType.FULL);
        run.setTriggerSource(SyncTriggerSource.MANUAL);
        run.setRequestedAt(LocalDateTime.of(2026, 8, 29, 8, 25));

        assertThat(mapper.insertRun(run)).isEqualTo(1);

        DramaSyncDisplayRunItem first = new DramaSyncDisplayRunItem();
        first.setRunId(run.getId());
        first.setTaskDomain(DramaSyncDomain.CATALOG);
        first.setTaskId(11L);
        DramaSyncDisplayRunItem second = new DramaSyncDisplayRunItem();
        second.setRunId(run.getId());
        second.setTaskDomain(DramaSyncDomain.CATALOG);
        second.setTaskId(12L);

        assertThat(mapper.insertItem(first)).isEqualTo(1);
        assertThat(mapper.insertItem(second)).isEqualTo(1);
        assertThat(mapper.findById(run.getId(), providerId, DramaSyncDomain.CATALOG))
                .extracting(DramaSyncDisplayRun::getTaskType, DramaSyncDisplayRun::getTriggerSource)
                .containsExactly(DramaSyncTaskType.FULL, SyncTriggerSource.MANUAL);
        assertThat(mapper.findItems(run.getId(), DramaSyncDomain.CATALOG))
                .extracting(DramaSyncDisplayRunItem::getTaskId)
                .containsExactly(11L, 12L);
    }
}
