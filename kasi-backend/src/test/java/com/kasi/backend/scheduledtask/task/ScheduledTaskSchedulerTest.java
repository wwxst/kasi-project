package com.kasi.backend.scheduledtask.task;

import com.kasi.backend.scheduledtask.service.ScheduledTaskDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("系统定时任务调度器")
class ScheduledTaskSchedulerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, ScheduledTaskScheduler.class);

    @Test
    @DisplayName("调度方法委托服务处理到期任务")
    void schedulerDelegatesDueBatch() {
        contextRunner.run(context -> {
            ScheduledTaskScheduler scheduler = context.getBean(ScheduledTaskScheduler.class);
            ScheduledTaskDispatchService service = context.getBean(ScheduledTaskDispatchService.class);

            scheduler.processDueTasks();

            verify(service).processDueBatch();
        });
    }

    @Test
    @DisplayName("关闭配置时不创建系统定时任务调度器")
    void disabledPropertyDoesNotCreateScheduler() {
        contextRunner.withPropertyValues("app.scheduled-task.scheduler-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledTaskScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        ScheduledTaskDispatchService scheduledTaskDispatchService() {
            return mock(ScheduledTaskDispatchService.class);
        }
    }
}
