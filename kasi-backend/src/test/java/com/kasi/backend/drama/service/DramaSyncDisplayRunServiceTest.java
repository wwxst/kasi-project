package com.kasi.backend.drama.service;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import com.kasi.backend.drama.mapper.DramaSyncDisplayRunMapper;
import com.kasi.backend.drama.service.impl.DramaSyncDisplayRunServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("同步展示运行服务")
class DramaSyncDisplayRunServiceTest {
    private final DramaSyncDisplayRunMapper mapper = mock(DramaSyncDisplayRunMapper.class);
    private final DramaSyncDisplayRunService service = new DramaSyncDisplayRunServiceImpl(mapper);

    {
        when(mapper.insertRun(any(DramaSyncDisplayRun.class))).thenReturn(1);
        when(mapper.insertItem(any(DramaSyncDisplayRunItem.class))).thenReturn(1);
    }

    @Test
    @DisplayName("创建运行并关联多个底层任务时共享同一个运行标识")
    void createsRunAndAttachesTasks() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 29, 8, 25);

        DramaSyncDisplayRun run = service.createRun(7L, null, DramaSyncDomain.CATALOG,
                DramaSyncTaskType.FULL, SyncTriggerSource.MANUAL, requestedAt);
        service.attachTask(run.getId(), DramaSyncDomain.CATALOG, 11L);
        service.attachTask(run.getId(), DramaSyncDomain.CATALOG, 12L);

        assertThat(run.getId()).isNotBlank();
        assertThat(run.getProviderId()).isEqualTo(7L);
        assertThat(run.getRequestedAt()).isEqualTo(requestedAt);
        verify(mapper).insertRun(any(DramaSyncDisplayRun.class));
        verify(mapper, times(2)).insertItem(any(DramaSyncDisplayRunItem.class));
    }

    @Test
    @DisplayName("目录运行可创建继承触发方式的剧集子运行")
    void createsChildRunWithInheritedTrigger() {
        DramaSyncDisplayRun parent = run("catalog-run", DramaSyncDomain.CATALOG,
                DramaSyncTaskType.INCREMENTAL, SyncTriggerSource.SCHEDULED);
        when(mapper.findById(parent.getId(), 7L, DramaSyncDomain.CONTENT)).thenReturn(null);
        when(mapper.findChildRun(parent.getId(), DramaSyncDomain.CONTENT,
                DramaSyncTaskType.CATALOG_AUTO)).thenReturn(null);

        DramaSyncDisplayRun child = service.createChildRun(parent, 7L,
                DramaSyncDomain.CONTENT, DramaSyncTaskType.CATALOG_AUTO,
                LocalDateTime.of(2026, 8, 29, 8, 26));

        assertThat(child.getParentRunId()).isEqualTo(parent.getId());
        assertThat(child.getTriggerSource()).isEqualTo(SyncTriggerSource.SCHEDULED);
        verify(mapper).insertRun(any(DramaSyncDisplayRun.class));
    }

    private DramaSyncDisplayRun run(String id, DramaSyncDomain domain,
                                    DramaSyncTaskType taskType, SyncTriggerSource trigger) {
        DramaSyncDisplayRun run = new DramaSyncDisplayRun();
        run.setId(id);
        run.setProviderId(7L);
        run.setDomain(domain);
        run.setTaskType(taskType);
        run.setTriggerSource(trigger);
        run.setRequestedAt(LocalDateTime.of(2026, 8, 29, 8, 25));
        return run;
    }

    private DramaSyncDisplayRunItem item(Long taskId) {
        DramaSyncDisplayRunItem item = new DramaSyncDisplayRunItem();
        item.setTaskId(taskId);
        item.setTaskDomain(DramaSyncDomain.CATALOG);
        return item;
    }
}
