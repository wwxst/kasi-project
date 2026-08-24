package com.kasi.backend.drama.task;

import com.kasi.backend.drama.service.DramaCatalogSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("短剧目录同步调度器")
class DramaCatalogSchedulerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, DramaCatalogScheduler.class);

    @Test
    @DisplayName("调度方法委托同步服务处理到期任务")
    void schedulerDelegatesDueBatch() {
        contextRunner.run(context -> {
            DramaCatalogScheduler scheduler = context.getBean(DramaCatalogScheduler.class);
            DramaCatalogSyncService service = context.getBean(DramaCatalogSyncService.class);

            scheduler.processDueDramas();

            verify(service).processDueBatch();
        });
    }

    @Test
    @DisplayName("关闭目录调度配置时不创建调度器")
    void disabledPropertyDoesNotCreateScheduler() {
        contextRunner.withPropertyValues("app.promotion.drama.sync.scheduler-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DramaCatalogScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        DramaCatalogSyncService dramaCatalogSyncService() {
            return mock(DramaCatalogSyncService.class);
        }
    }
}
