package com.kasi.backend.auth.service;

import com.kasi.backend.auth.entity.PasswordResetTokenReservation;
import com.kasi.backend.auth.service.impl.PasswordResetTokenServiceImpl;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetTokenServiceRedisTest {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private PasswordResetTokenService service;

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
        service = new PasswordResetTokenServiceImpl(redisTemplate, 600);
    }

    @Test
    @DisplayName("同一账号签发新重置凭证会原子撤销旧凭证")
    void issuingNewTokenRevokesPreviousToken() {
        String oldToken = service.generateResetToken(7L, SubjectType.USER);
        String newToken = service.generateResetToken(7L, SubjectType.USER);

        assertResetTokenCannotBeReserved(oldToken);
        PasswordResetTokenReservation reservation = service.reserveToken(newToken);
        assertEquals(7L, reservation.userId());
        assertEquals(SubjectType.USER, reservation.subjectType());
    }

    @Test
    @DisplayName("并发预占同一重置凭证只能成功一次")
    void concurrentReservationsOnlyOneSucceeds() throws Exception {
        String rawToken = service.generateResetToken(8L, SubjectType.USER);
        int requestCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            service.reserveToken(rawToken);
                            return true;
                        } catch (RuntimeException exception) {
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
            assertEquals(1, successCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("同一账号并发签发后只有一个重置凭证有效")
    void concurrentIssuanceLeavesOnlyOneValidToken() throws Exception {
        int requestCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<String>> results = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.generateResetToken(15L, SubjectType.USER);
                    }))
                    .toList();

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long validCount = 0;
            for (Future<String> result : results) {
                try {
                    service.reserveToken(result.get(5, TimeUnit.SECONDS));
                    validCount++;
                } catch (BusinessException exception) {
                    // Every superseded token must be invalid.
                }
            }

            assertEquals(1, validCount);
            assertEquals(1, redisTemplate.keys("pwd:token:*").size());
            assertEquals(1, redisTemplate.keys("pwd:user:*").size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("完成凭证只删除匹配的PROCESSING凭证和反向索引")
    void completeDeletesOnlyMatchingReservation() {
        String rawToken = service.generateResetToken(9L, SubjectType.USER);
        PasswordResetTokenReservation reservation = service.reserveToken(rawToken);
        String tokenKey = tokenKey(reservation.tokenHash());
        String userKey = userKey(SubjectType.USER, 9L);

        service.completeToken(new PasswordResetTokenReservation(
                99L, SubjectType.USER, reservation.tokenHash()));
        assertTrue(redisTemplate.hasKey(tokenKey));
        assertEquals(reservation.tokenHash(), redisTemplate.opsForValue().get(userKey));

        service.completeToken(reservation);
        assertFalse(redisTemplate.hasKey(tokenKey));
        assertFalse(redisTemplate.hasKey(userKey));
    }

    @Test
    @DisplayName("恢复READY只对仍属于当前凭证的PROCESSING状态生效")
    void restoreReadyMakesReservationUsableAgain() {
        String rawToken = service.generateResetToken(10L, SubjectType.USER);
        PasswordResetTokenReservation reservation = service.reserveToken(rawToken);
        String tokenKey = tokenKey(reservation.tokenHash());
        String userKey = userKey(SubjectType.USER, 10L);
        assertEquals("PROCESSING|USER:10", redisTemplate.opsForValue().get(tokenKey));

        service.restoreReady(reservation);
        assertEquals("READY|USER:10", redisTemplate.opsForValue().get(tokenKey));
        assertEquals(reservation.tokenHash(), redisTemplate.opsForValue().get(userKey));
        assertTrue(redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redisTemplate.getExpire(userKey, TimeUnit.MILLISECONDS) > 0);

        PasswordResetTokenReservation retried = service.reserveToken(rawToken);
        assertEquals(reservation.tokenHash(), retried.tokenHash());
    }

    @Test
    @DisplayName("签发和预占期间两个Redis键都保留有限TTL")
    void tokenAndReverseIndexHaveFiniteTtl() {
        String rawToken = service.generateResetToken(11L, SubjectType.USER);
        String hash = hash(rawToken);
        String tokenKey = tokenKey(hash);
        String userKey = userKey(SubjectType.USER, 11L);

        assertNotNull(redisTemplate.opsForValue().get(tokenKey));
        assertTrue(redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redisTemplate.getExpire(userKey, TimeUnit.MILLISECONDS) > 0);

        service.reserveToken(rawToken);
        assertTrue(redisTemplate.getExpire(tokenKey, TimeUnit.MILLISECONDS) > 0);
        assertTrue(redisTemplate.getExpire(userKey, TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    @DisplayName("永久重置凭证Key不得进入PROCESSING状态")
    void permanentTokenKeyCannotBeReserved() {
        String rawToken = service.generateResetToken(12L, SubjectType.USER);
        String tokenKey = tokenKey(hash(rawToken));
        assertTrue(redisTemplate.persist(tokenKey));

        assertThrows(BusinessException.class, () -> service.reserveToken(rawToken));
        assertEquals("READY|USER:12", redisTemplate.opsForValue().get(tokenKey));
    }

    @Test
    @DisplayName("永久反向索引Key不得授权重置凭证预占")
    void permanentReverseIndexCannotAuthorizeReservation() {
        String rawToken = service.generateResetToken(13L, SubjectType.USER);
        String tokenKey = tokenKey(hash(rawToken));
        String userKey = userKey(SubjectType.USER, 13L);
        assertTrue(redisTemplate.persist(userKey));

        assertThrows(BusinessException.class, () -> service.reserveToken(rawToken));
        assertEquals("READY|USER:13", redisTemplate.opsForValue().get(tokenKey));
    }

    private void assertResetTokenCannotBeReserved(String rawToken) {
        assertThrows(BusinessException.class, () -> service.reserveToken(rawToken));
    }

    private static String tokenKey(String hash) {
        return "pwd:token:" + hash;
    }

    private static String userKey(SubjectType subjectType, Long userId) {
        return "pwd:user:" + subjectType.name() + ":" + userId;
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
