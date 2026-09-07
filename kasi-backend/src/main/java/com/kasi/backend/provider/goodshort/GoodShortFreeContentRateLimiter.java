package com.kasi.backend.provider.goodshort;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class GoodShortFreeContentRateLimiter {
    private static final Duration DEFAULT_INTERVAL = Duration.ofMillis(650);

    private final Duration interval;
    private final LongSupplier nanoTime;
    private final LongConsumer sleeper;
    private final Map<String, Long> nextAllowedAt = new HashMap<>();

    GoodShortFreeContentRateLimiter() {
        this(DEFAULT_INTERVAL, System::nanoTime, LockSupport::parkNanos);
    }

    GoodShortFreeContentRateLimiter(Duration interval, LongSupplier nanoTime, LongConsumer sleeper) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Free-content rate-limit interval must be positive");
        }
        this.interval = interval;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    void acquire(String connectionKey) {
        synchronized (nextAllowedAt) {
            long now = nanoTime.getAsLong();
            long allowedAt = nextAllowedAt.getOrDefault(connectionKey, now);
            long waitNanos = allowedAt - now;
            if (waitNanos > 0) {
                sleeper.accept(waitNanos);
                now = nanoTime.getAsLong();
            }
            nextAllowedAt.put(connectionKey, now + interval.toNanos());
        }
    }
}
