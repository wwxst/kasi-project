package com.kasi.backend.security.service.impl;

import com.kasi.backend.security.entity.AuthSession;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.security.context.AuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Redis 账号版本和单会话状态。 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final String VERSION_PREFIX = "auth:version:";
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String ACTIVE_PREFIX = "ACTIVE:";
    private static final String MUTATING_PREFIX = "MUTATING:";
    private static final long VERSION_TTL_GRACE_SECONDS = 300;

    private static final DefaultRedisScript<String> CREATE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            local version = redis.call('get', KEYS[1])
            local versionTtl = redis.call('ttl', KEYS[1])
            if version and versionTtl > 0 and string.sub(version, 1, 9) == 'MUTATING:' then
                return 'MUTATING'
            end
            if not version or versionTtl <= 0 or string.sub(version, 1, 7) ~= 'ACTIVE:' then
                version = ARGV[1]
            end
            redis.call('setex', KEYS[1], ARGV[2], version)
            redis.call('setex', KEYS[2], ARGV[3], ARGV[4] .. version)
            return version
            """, String.class);

    private static final DefaultRedisScript<Long> VALIDATE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ttl', KEYS[1]) <= 0 or redis.call('ttl', KEYS[2]) <= 0 then
                return 0
            end
            local version = redis.call('get', KEYS[1])
            if not version or string.sub(version, 1, 7) ~= 'ACTIVE:' or version ~= ARGV[1] then
                return 0
            end
            if redis.call('get', KEYS[2]) ~= ARGV[2] then
                return 0
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_VERSION_SCRIPT = new DefaultRedisScript<>("""
            redis.call('setex', KEYS[1], ARGV[1], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> BEGIN_MUTATION_SCRIPT = new DefaultRedisScript<>("""
            local nonce = ARGV[2]
            redis.call('setex', KEYS[1], ARGV[1], 'MUTATING:' .. nonce)
            return nonce
            """, String.class);

    private static final DefaultRedisScript<Long> COMPLETE_MUTATION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ttl', KEYS[1]) <= 0 then
                return 0
            end
            if redis.call('get', KEYS[1]) ~= 'MUTATING:' .. ARGV[1] then
                return 0
            end
            redis.call('setex', KEYS[1], ARGV[2], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long jwtExpiration;
    private final long versionExpiration;

    public SessionServiceImpl(StringRedisTemplate redisTemplate,
                          @Value("${app.jwt.expiration:7200}") long jwtExpiration) {
        this.redisTemplate = redisTemplate;
        this.jwtExpiration = jwtExpiration;
        this.versionExpiration = jwtExpiration + VERSION_TTL_GRACE_SECONDS;
    }

    /** 正确凭据登录后，原子创建或续期账号版本及单会话。 */
    @Override
    public AuthSession createSession(SubjectType subjectType, Long subjectId) {
        String jti = UUID.randomUUID().toString();
        String newVersion = ACTIVE_PREFIX + UUID.randomUUID();
        String subjectPrefix = subjectType.name() + ":" + subjectId + ":";
        try {
            String version = redisTemplate.execute(
                    CREATE_SESSION_SCRIPT,
                    List.of(versionKey(subjectType, subjectId), sessionKey(jti)),
                    newVersion,
                    String.valueOf(versionExpiration),
                    String.valueOf(jwtExpiration),
                    subjectPrefix);
            if ("MUTATING".equals(version)) {
                throw new AuthStateUnavailableException(
                        new IllegalStateException("账号正在进行敏感状态修改"));
            }
            if (version == null) {
                throw new AuthStateUnavailableException(
                        new IllegalStateException("Redis 未返回会话版本"));
            }
            return new AuthSession(jti, version);
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    /** 单次 Lua 同时校验账号版本和 jti 会话，避免旋转期间的校验竞态。 */
    @Override
    public boolean isValid(AuthContext context) {
        String expectedSession = sessionValue(
                context.getSubjectType(), context.getSubjectId(), context.getSessionVersion());
        try {
            Long result = redisTemplate.execute(
                    VALIDATE_SESSION_SCRIPT,
                    List.of(versionKey(context.getSubjectType(), context.getSubjectId()),
                            sessionKey(context.getJti())),
                    context.getSessionVersion(),
                    expectedSession);
            return Long.valueOf(1L).equals(result);
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    /** 仅撤销当前设备的单会话。 */
    @Override
    public void revokeSession(String jti) {
        try {
            redisTemplate.delete(sessionKey(jti));
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    /** 在关键 MySQL 状态变更前旋转版本，使全部旧 JWT 立即失效。 */
    @Override
    public void rotateSessionVersion(SubjectType subjectType, Long subjectId) {
        SessionMutation mutation = beginMutation(subjectType, subjectId);
        completeMutation(mutation);
    }

    /** 关键 MySQL 写操作前原子切换为 MUTATING，阻止旧会话和并发新登录。 */
    @Override
    public SessionMutation beginMutation(SubjectType subjectType, Long subjectId) {
        String nonce = UUID.randomUUID().toString();
        try {
            String result = redisTemplate.execute(
                    BEGIN_MUTATION_SCRIPT,
                    List.of(versionKey(subjectType, subjectId)),
                    String.valueOf(versionExpiration),
                    nonce);
            if (!nonce.equals(result)) {
                throw new AuthStateUnavailableException(
                        new IllegalStateException("Redis 未确认账号进入修改状态"));
            }
            return new SessionMutation(subjectType, subjectId, nonce);
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    /** MySQL 成功提交后，只有 nonce 仍匹配时才恢复 ACTIVE。 */
    @Override
    public void completeMutation(SessionMutation mutation) {
        String activeVersion = ACTIVE_PREFIX + UUID.randomUUID();
        try {
            Long result = redisTemplate.execute(
                    COMPLETE_MUTATION_SCRIPT,
                    List.of(versionKey(mutation.subjectType(), mutation.subjectId())),
                    mutation.nonce(),
                    String.valueOf(versionExpiration),
                    activeVersion);
            if (!Long.valueOf(1L).equals(result)) {
                throw new AuthStateUnavailableException(
                        new IllegalStateException("Redis 未确认账号修改完成"));
            }
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    String versionKey(SubjectType subjectType, Long subjectId) {
        return VERSION_PREFIX + subjectType.name() + ":" + subjectId;
    }

    String sessionKey(String jti) {
        return SESSION_PREFIX + jti;
    }

    private String sessionValue(SubjectType subjectType, Long subjectId, String sessionVersion) {
        return subjectType.name() + ":" + subjectId + ":" + sessionVersion;
    }
}
