package com.kasi.backend.scheduledtask.service.impl;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.DramaCatalogSyncService;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.scheduledtask.config.ScheduledTaskProperties;
import com.kasi.backend.scheduledtask.entity.SystemScheduledTask;
import com.kasi.backend.scheduledtask.mapper.SystemScheduledTaskMapper;
import com.kasi.backend.scheduledtask.service.ScheduledTaskDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ScheduledTaskDispatchServiceImpl implements ScheduledTaskDispatchService {
    private final SystemScheduledTaskMapper taskMapper;
    private final ShortDramaProviderMapper providerMapper;
    private final DramaCatalogSyncService syncService;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledTaskProperties properties;
    private final DramaSyncProperties dramaProperties;
    private final Clock clock;
    private final String workerId;

    public ScheduledTaskDispatchServiceImpl(SystemScheduledTaskMapper taskMapper,
                                            ShortDramaProviderMapper providerMapper,
                                            DramaCatalogSyncService syncService,
                                            PlatformTransactionManager transactionManager,
                                            ScheduledTaskProperties properties,
                                            DramaSyncProperties dramaProperties,
                                            Clock clock,
                                            @Value("${app.scheduled-task.worker-id:${random.uuid}}")
                                            String workerId) {
        this.taskMapper = taskMapper;
        this.providerMapper = providerMapper;
        this.syncService = syncService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.dramaProperties = dramaProperties;
        this.clock = clock;
        this.workerId = workerId;
    }

    @Override
    public void processDueBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SystemScheduledTask> due = taskMapper.findDue(now, properties.getBatchSize());
        if (due == null) {
            return;
        }
        for (SystemScheduledTask task : due) {
            if (!claim(task, now)) {
                continue;
            }
            try {
                dispatch(task);
            } catch (RuntimeException exception) {
                log.error("系统定时任务执行失败: taskCode={}", task.getTaskCode(), exception);
            } finally {
                taskMapper.completeRun(task.getId(), workerId,
                        now.plusMinutes(task.getIntervalMinutes()));
            }
        }
    }

    private boolean claim(SystemScheduledTask task, LocalDateTime now) {
        Boolean claimed = transactionTemplate.execute(status ->
                taskMapper.claimLease(task.getId(), workerId, now,
                        now.plus(properties.getLeaseDuration())) == 1);
        return Boolean.TRUE.equals(claimed);
    }

    private void dispatch(SystemScheduledTask task) {
        switch (task.getTaskCode()) {
            case GOODSHORT_DRAMA_INCREMENTAL_SYNC -> dispatchGoodShortDramaIncremental();
        }
    }

    private void dispatchGoodShortDramaIncremental() {
        ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");
        if (provider == null) {
            return;
        }
        syncService.requestScheduledIncremental(provider.getId(), dramaProperties.getLanguages());
    }
}
