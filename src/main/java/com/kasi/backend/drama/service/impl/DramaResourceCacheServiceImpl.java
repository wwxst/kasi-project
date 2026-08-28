package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.service.DramaResourceCacheService;
import com.kasi.backend.provider.spi.FreeContentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class DramaResourceCacheServiceImpl implements DramaResourceCacheService {
    private static final String VALUE_PREFIX = "drama:free-content:";
    private static final String LOCK_PREFIX = "drama:free-content:lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Duration lockTtl;

    public DramaResourceCacheServiceImpl(StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         @Value("${app.drama.resource-cache-ttl:5m}") Duration ttl,
                                         @Value("${app.drama.resource-lock-ttl:10s}") Duration lockTtl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.lockTtl = lockTtl;
    }

    @Override
    public List<FreeContentResult> get(Long dramaId, Supplier<List<FreeContentResult>> loader) {
        String key = VALUE_PREFIX + dramaId;
        List<FreeContentResult> cached = read(key);
        if (cached != null) return cached;

        String lockKey = LOCK_PREFIX + dramaId;
        String token = UUID.randomUUID().toString();
        boolean acquired = acquire(lockKey, token);
        if (!acquired) {
            cached = awaitCache(key);
            if (cached != null) return cached;
        }
        try {
            List<FreeContentResult> loaded = List.copyOf(loader.get());
            write(key, loaded);
            return loaded;
        } finally {
            if (acquired) release(lockKey, token);
        }
    }

    @Override
    public void evict(Long dramaId) {
        try {
            redisTemplate.delete(VALUE_PREFIX + dramaId);
        } catch (RuntimeException ignored) {
            // Cache eviction must not hide the provider result from the caller.
        }
    }

    private List<FreeContentResult> read(String key) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            return null;
        }
        if (json == null || json.isBlank()) return null;
        try {
            return List.of(objectMapper.readValue(json, FreeContentResult[].class));
        } catch (JacksonException exception) {
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
                // A broken cache is treated as a miss even if deletion fails.
            }
            return null;
        }
    }

    private void write(String key, List<FreeContentResult> value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException ignored) {
            // Redis is an optimization; the fresh provider response remains usable.
        }
    }

    private boolean acquire(String lockKey, String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<FreeContentResult> awaitCache(String key) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
            List<FreeContentResult> value = read(key);
            if (value != null) return value;
        }
        return null;
    }

    private void release(String lockKey, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token);
        } catch (RuntimeException ignored) {
            // The short TTL releases the lock if Redis is unavailable here.
        }
    }
}
