package com.kasi.backend.provider.goodshort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GoodShortClientConfigTest {
    @Test
    @DisplayName("平台时钟使用上海时区以匹配数据库任务时间")
    void providerClockUsesShanghaiZone() {
        assertThat(new GoodShortClientConfig().providerClock().getZone())
                .isEqualTo(ZoneId.of("Asia/Shanghai"));
    }
}
