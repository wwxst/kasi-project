package com.kasi.backend.provider.goodshort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GoodShort目录限流")
class GoodShortCatalogRateLimiterTest {

    @Test
    @DisplayName("同一接入账号的连续请求等待最小间隔")
    void waitsBetweenRequestsForSameConnection() {
        AtomicLong now = new AtomicLong(0);
        AtomicLong slept = new AtomicLong();
        GoodShortCatalogRateLimiter limiter = new GoodShortCatalogRateLimiter(
                Duration.ofMillis(610), now::get, nanos -> {
                    slept.addAndGet(nanos);
                    now.addAndGet(nanos);
                });

        limiter.acquire("https://goodshort.test|partner-1");
        limiter.acquire("https://goodshort.test|partner-1");

        assertThat(slept).hasValue(Duration.ofMillis(610).toNanos());
    }
}
