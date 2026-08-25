package com.kasi.backend.security.service;

import com.kasi.backend.security.entity.AuthSession;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.context.AuthContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Redis 会话状态")
class SessionServiceTest extends BaseAuthTest {

    @Autowired
    private SessionService sessionService;

    @Test
    @DisplayName("版本不存在时旧JWT直接失效且不重建")
    void missingVersionInvalidatesTokenWithoutRebuilding() {
        AuthContext context = context("jti-1", "version-1");

        assertFalse(sessionService.isValid(context));
        assertFalse(redisTemplate.hasKey("auth:version:USER:7"));
    }

    @Test
    @DisplayName("登录原子创建版本和单会话且两个Key都有TTL")
    void createSessionStoresVersionAndSessionWithTtl() {
        AuthSession session = sessionService.createSession(SubjectType.USER, 7L);
        AuthContext context = context(session.jti(), session.sessionVersion());

        assertTrue(sessionService.isValid(context));
        assertTrue(redisTemplate.getExpire("auth:version:USER:7", TimeUnit.SECONDS) > 0);
        assertTrue(redisTemplate.getExpire("auth:session:" + session.jti(), TimeUnit.SECONDS) > 0);
    }

    @Test
    @DisplayName("永久账号版本Key不得通过认证")
    void permanentVersionKeyInvalidatesToken() {
        redisTemplate.opsForValue().set("auth:version:USER:7", "version-1");
        redisTemplate.opsForValue().set(
                "auth:session:jti-1", "USER:7:version-1", Duration.ofMinutes(5));

        assertFalse(sessionService.isValid(context("jti-1", "version-1")));
    }

    @Test
    @DisplayName("永久单会话Key不得通过认证")
    void permanentSessionKeyInvalidatesToken() {
        redisTemplate.opsForValue().set(
                "auth:version:USER:7", "version-1", Duration.ofMinutes(5));
        redisTemplate.opsForValue().set("auth:session:jti-1", "USER:7:version-1");

        assertFalse(sessionService.isValid(context("jti-1", "version-1")));
    }

    @Test
    @DisplayName("旋转账号版本后全部旧会话失效")
    void rotateVersionInvalidatesExistingSessions() {
        AuthSession first = sessionService.createSession(SubjectType.USER, 7L);
        AuthSession second = sessionService.createSession(SubjectType.USER, 7L);

        sessionService.rotateSessionVersion(SubjectType.USER, 7L);

        assertFalse(sessionService.isValid(context(first.jti(), first.sessionVersion())));
        assertFalse(sessionService.isValid(context(second.jti(), second.sessionVersion())));
    }

    @Test
    @DisplayName("敏感操作先切换MUTATING并在完成后恢复ACTIVE")
    void mutationVersionFailsClosedUntilCompleted() {
        AuthSession oldSession = sessionService.createSession(SubjectType.USER, 7L);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, 7L);

        assertFalse(sessionService.isValid(context(oldSession.jti(), oldSession.sessionVersion())));
        assertFalse(redisTemplate.opsForValue().get("auth:version:USER:7").startsWith("ACTIVE:"));

        sessionService.completeMutation(mutation);
        AuthSession newSession = sessionService.createSession(SubjectType.USER, 7L);
        assertTrue(sessionService.isValid(context(newSession.jti(), newSession.sessionVersion())));
    }

    @Test
    @DisplayName("事务回滚后恢复ACTIVE并允许重新建立会话")
    void rollbackMutationRestoresActiveSessionVersion() {
        sessionService.createSession(SubjectType.USER, 7L);
        SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, 7L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            sessionService.registerMutationCompletion(mutation);

            TransactionSynchronization synchronization =
                    TransactionSynchronizationManager.getSynchronizations().get(0);
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            AuthSession newSession = sessionService.createSession(SubjectType.USER, 7L);
            assertTrue(sessionService.isValid(context(newSession.jti(), newSession.sessionVersion())));
            assertTrue(redisTemplate.opsForValue().get("auth:version:USER:7").startsWith("ACTIVE:"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AuthContext context(String jti, String version) {
        return AuthContext.builder()
                .subjectId(7L)
                .subjectType(SubjectType.USER)
                .username("user")
                .jti(jti)
                .sessionVersion(version)
                .build();
    }
}
