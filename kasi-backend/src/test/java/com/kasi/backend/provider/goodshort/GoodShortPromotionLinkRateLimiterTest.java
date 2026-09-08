package com.kasi.backend.provider.goodshort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoodShort推广链接限流")
class GoodShortPromotionLinkRateLimiterTest {

    @Test
    @DisplayName("同一甲方唯一组合的连续请求等待两秒")
    void waitsTwoSecondsForSameProviderTuple() {
        AtomicLong now = new AtomicLong(0);
        AtomicLong slept = new AtomicLong();
        GoodShortPromotionLinkRateLimiter limiter = new GoodShortPromotionLinkRateLimiter(
                Duration.ofSeconds(2), now::get, nanos -> {
                    slept.addAndGet(nanos);
                    now.addAndGet(nanos);
                });

        limiter.acquire("pid|book|user|TIKTOK");
        limiter.acquire("pid|book|user|TIKTOK");
        limiter.acquire("pid|book|user|YOUTUBE");

        assertThat(slept).hasValue(Duration.ofSeconds(2).toNanos());
    }
}
