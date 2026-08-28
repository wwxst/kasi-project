package com.kasi.backend.drama.service;

import com.kasi.backend.drama.service.impl.DramaResourceCacheServiceImpl;
import com.kasi.backend.provider.spi.FreeContentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DramaResourceCacheServiceTest {
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private AtomicReference<String> cached;
    private DramaResourceCacheService service;

    @BeforeEach
    void setUp() {
        cached = new AtomicReference<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("drama:free-content:7")).thenAnswer(invocation -> cached.get());
        when(valueOperations.setIfAbsent(eq("drama:free-content:lock:7"), anyString(), any(Duration.class)))
                .thenReturn(true);
        doAnswer(invocation -> {
            cached.set(invocation.getArgument(1, String.class));
            return null;
        }).when(valueOperations).set(eq("drama:free-content:7"), anyString(), any(Duration.class));
        service = new DramaResourceCacheServiceImpl(redisTemplate, JsonMapper.builder().build(),
                Duration.ofMinutes(5), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("TTL内重复读取只调用一次远端加载器")
    void getUsesCachedResourceWithinTtl() {
        AtomicInteger loads = new AtomicInteger();

        List<FreeContentResult> first = service.get(7L, () -> load(loads));
        List<FreeContentResult> second = service.get(7L, () -> load(loads));

        assertThat(first).isEqualTo(second);
        assertThat(loads).hasValue(1);
    }

    @Test
    @DisplayName("缓存JSON损坏时删除并重新拉取")
    void getRefreshesCorruptedCache() {
        cached.set("{broken-json");
        AtomicInteger loads = new AtomicInteger();

        List<FreeContentResult> result = service.get(7L, () -> load(loads));

        assertThat(result).containsExactly(new FreeContentResult("Chapter 1", "https://cdn.test/1.m3u8"));
        assertThat(loads).hasValue(1);
        verify(redisTemplate).delete("drama:free-content:7");
    }

    private List<FreeContentResult> load(AtomicInteger loads) {
        loads.incrementAndGet();
        return List.of(new FreeContentResult("Chapter 1", "https://cdn.test/1.m3u8"));
    }
}
