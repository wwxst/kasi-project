package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import com.kasi.backend.drama.enums.DramaSyncTaskType;
import com.kasi.backend.drama.enums.SyncTriggerSource;
import com.kasi.backend.drama.mapper.DramaSyncDisplayRunMapper;
import com.kasi.backend.drama.service.DramaSyncDisplayRunService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DramaSyncDisplayRunServiceImpl implements DramaSyncDisplayRunService {
    private final DramaSyncDisplayRunMapper mapper;

    public DramaSyncDisplayRunServiceImpl(DramaSyncDisplayRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DramaSyncDisplayRun createRun(Long providerId, String parentRunId, DramaSyncDomain domain,
                                         DramaSyncTaskType taskType, SyncTriggerSource triggerSource,
                                         LocalDateTime requestedAt) {
        DramaSyncDisplayRun run = new DramaSyncDisplayRun();
        run.setId(UUID.randomUUID().toString());
        run.setProviderId(providerId);
        run.setParentRunId(parentRunId);
        run.setDomain(domain);
        run.setTaskType(taskType);
        run.setTriggerSource(triggerSource);
        run.setRequestedAt(requestedAt);
        if (mapper.insertRun(run) != 1) {
            throw new IllegalStateException("同步展示运行保存失败");
        }
        return run;
    }

    @Override
    public DramaSyncDisplayRun createChildRun(DramaSyncDisplayRun parent, Long providerId,
                                              DramaSyncDomain domain, DramaSyncTaskType taskType,
                                              LocalDateTime requestedAt) {
        if (parent == null) {
            throw new IllegalArgumentException("同步展示父运行不能为空");
        }
        return createRun(providerId, parent.getId(), domain, taskType,
                parent.getTriggerSource(), requestedAt);
    }

    @Override
    public void attachTask(String runId, DramaSyncDomain domain, Long taskId) {
        DramaSyncDisplayRunItem item = new DramaSyncDisplayRunItem();
        item.setRunId(runId);
        item.setTaskDomain(domain);
        item.setTaskId(taskId);
        mapper.deleteItemByTask(domain, taskId);
        if (mapper.insertItem(item) != 1) {
            throw new IllegalStateException("同步展示子任务关联保存失败");
        }
    }

    @Override
    public void updateTaskType(String runId, DramaSyncTaskType taskType) {
        if (mapper.updateTaskType(runId, taskType) != 1) {
            throw new IllegalStateException("同步展示运行类型更新失败");
        }
    }
}
