package com.kasi.backend.provider.goodshort;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class GoodShortFilingRateLimiterTest {

    @Test
    void throttlesEachOperationKeyIndependently() {
        AtomicLong now = new AtomicLong();
        List<Long> waits = new ArrayList<>();
        GoodShortFilingRateLimiter limiter = new GoodShortFilingRateLimiter(
                Duration.ofMillis(650), now::get, nanos -> {
                    waits.add(nanos);
                    now.addAndGet(nanos);
                });

        limiter.acquire("connection|REPORT");
        limiter.acquire("connection|QUERY");
        limiter.acquire("connection|REPORT");
        limiter.acquire("connection|QUERY");

        assertThat(waits).containsExactly(Duration.ofMillis(650).toNanos());
    }
}
