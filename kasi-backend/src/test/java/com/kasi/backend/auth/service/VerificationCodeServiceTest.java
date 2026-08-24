package com.kasi.backend.auth.service;

import com.kasi.backend.auth.service.impl.VerificationCodeServiceImpl;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

class VerificationCodeServiceTest {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private VerificationCodeService service;

    @BeforeAll
    static void startRedis() throws IOException {
        int port = findAvailablePort();
        redisServer = RedisServer.builder()
                .port(port)
                .setting("maxheap 128mb")
                .setting("maxmemory 64mb")
                .setting("save \"\"")
                .build();
        redisServer.start();

        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", port));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        service = new VerificationCodeServiceImpl(redisTemplate, mock(VerificationCodeSender.class));
        ReflectionTestUtils.setField(service, "codeExpiration", 300);
    }

    @Test
    @DisplayName("并发校验正确验证码时只能成功消费一次")
    void verifyCodeWithConcurrentCorrectRequestsOnlyOneSucceeds() throws Exception {
        String target = "13800138000";
        VerificationScene scene = VerificationScene.REGISTER;
        String code = "123456";
        String codeKey = codeKey(target, scene);
        String failKey = failKey(target, scene);
        int requestCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            for (int round = 0; round < 10; round++) {
                redisTemplate.opsForValue().set(codeKey, hash(code), Duration.ofMinutes(5));
                CountDownLatch ready = new CountDownLatch(requestCount);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<Boolean>> results = java.util.stream.IntStream.range(0, requestCount)
                        .mapToObj(ignored -> executor.submit(() -> {
                            ready.countDown();
                            start.await();
                            try {
                                return service.verifyCode(target, scene, code);
                            } catch (BusinessException exception) {
                                assertEquals(ErrorCode.VERIFICATION_CODE_ERROR.getCode(), exception.getCode());
                                return false;
                            }
                        }))
                        .toList();

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                long successCount = 0;
                for (Future<Boolean> result : results) {
                    if (result.get(5, TimeUnit.SECONDS)) {
                        successCount++;
                    }
                }

                assertEquals(1, successCount, "同一验证码在并发请求中只能成功一次");
                assertFalse(redisTemplate.hasKey(codeKey));
                assertFalse(redisTemplate.hasKey(failKey));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("错误验证码达到五次时删除验证码并保留失败计数")
    void verifyCodeWithFifthWrongAttemptDeletesCode() {
        String target = "user@example.com";
        VerificationScene scene = VerificationScene.RESET_PASSWORD;
        String codeKey = codeKey(target, scene);
        String failKey = failKey(target, scene);
        redisTemplate.opsForValue().set(codeKey, hash("123456"), Duration.ofMinutes(5));

        for (int attempt = 1; attempt < 5; attempt++) {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.verifyCode(target, scene, "654321"));
            assertEquals(ErrorCode.VERIFICATION_CODE_ERROR.getCode(), exception.getCode());
            assertEquals(String.valueOf(attempt), redisTemplate.opsForValue().get(failKey));
            assertTrue(redisTemplate.hasKey(codeKey));
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verifyCode(target, scene, "654321"));

        assertEquals(ErrorCode.VERIFICATION_CODE_ERROR.getCode(), exception.getCode());
        assertEquals("5", redisTemplate.opsForValue().get(failKey));
        assertFalse(redisTemplate.hasKey(codeKey));
        assertTrue(redisTemplate.getExpire(failKey, TimeUnit.SECONDS) > 0);
    }

    @Test
    @DisplayName("并发错误校验达到上限后失败计数不再增长")
    void verifyCodeWithConcurrentWrongRequestsStopsAtMaximumAttempts() throws Exception {
        String target = "13900139000";
        VerificationScene scene = VerificationScene.REGISTER;
        String codeKey = codeKey(target, scene);
        String failKey = failKey(target, scene);
        int requestCount = 16;
        redisTemplate.opsForValue().set(codeKey, hash("123456"), Duration.ofMinutes(5));
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var results = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        assertThrows(BusinessException.class,
                                () -> service.verifyCode(target, scene, "654321"));
                        return null;
                    }))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }

            assertEquals("5", redisTemplate.opsForValue().get(failKey));
            assertFalse(redisTemplate.hasKey(codeKey));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Redis异常必须原样向上传播")
    void verifyCodeWhenRedisFailsFailsClosed() {
        RedisConnectionFailureException redisFailure =
                new RedisConnectionFailureException("redis unavailable");
        StringRedisTemplate unavailableRedis = mock(StringRedisTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("execute")) {
                throw redisFailure;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        VerificationCodeService unavailableService = new VerificationCodeServiceImpl(
                unavailableRedis, mock(VerificationCodeSender.class));
        ReflectionTestUtils.setField(unavailableService, "codeExpiration", 300);

        AuthStateUnavailableException thrown = assertThrows(AuthStateUnavailableException.class,
                () -> unavailableService.verifyCode("13800138000", VerificationScene.REGISTER, "123456"));

        assertSame(redisFailure, thrown.getCause());
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String codeKey(String target, VerificationScene scene) {
        return "vc:code:" + target + ":" + scene.name();
    }

    private static String failKey(String target, VerificationScene scene) {
        return "vc:fail:" + target + ":" + scene.name();
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
