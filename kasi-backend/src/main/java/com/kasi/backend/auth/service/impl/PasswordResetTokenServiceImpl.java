package com.kasi.backend.auth.service.impl;

import com.kasi.backend.auth.entity.PasswordResetTokenReservation;
import com.kasi.backend.auth.service.PasswordResetTokenService;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.common.enums.SubjectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 密码重置Token服务（Redis版）
 * <p>
 * 使用Redis存储重置Token，利用TTL自动过期，无需手动清理。
 * <p>
 * Redis Key: pwd:token:{tokenHash} → userId, TTL=tokenExpiration秒
 */
@Slf4j
@Service
public class PasswordResetTokenServiceImpl implements PasswordResetTokenService {

    private static final String TOKEN_PREFIX = "pwd:token:";
    private static final String USER_PREFIX = "pwd:user:";

    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('get', KEYS[1])
            if previous then
                redis.call('del', ARGV[1] .. previous)
            end
            redis.call('setex', ARGV[1] .. ARGV[2], ARGV[5], ARGV[3])
            redis.call('setex', KEYS[1], ARGV[5], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<String> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('get', KEYS[1])
            if not value or string.sub(value, 1, 6) ~= 'READY|' then
                return nil
            end
            local payload = string.sub(value, 7)
            local reverseKey = ARGV[1] .. payload
            local ttl = redis.call('pttl', KEYS[1])
            if ttl <= 0 or redis.call('pttl', reverseKey) <= 0 then
                return nil
            end
            if redis.call('get', reverseKey) ~= ARGV[2] then
                return nil
            end
            redis.call('psetex', KEYS[1], ttl, 'PROCESSING|' .. payload)
            return payload
            """, String.class);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('pttl', KEYS[1]) <= 0 or redis.call('pttl', KEYS[2]) <= 0 then
                return 0
            end
            if redis.call('get', KEYS[1]) ~= 'PROCESSING|' .. ARGV[1] then
                return 0
            end
            if redis.call('get', KEYS[2]) ~= ARGV[2] then
                return 0
            end
            redis.call('del', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RESTORE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('get', KEYS[1])
            if value ~= 'PROCESSING|' .. ARGV[1] then
                return 0
            end
            local ttl = redis.call('pttl', KEYS[1])
            if ttl <= 0 or redis.call('pttl', KEYS[2]) <= 0 then
                return 0
            end
            if redis.call('get', KEYS[2]) ~= ARGV[2] then
                return 0
            end
            redis.call('psetex', KEYS[1], ttl, 'READY|' .. ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int tokenExpiration;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== Key构建 ====================

    public PasswordResetTokenServiceImpl(StringRedisTemplate redisTemplate,
                                     @Value("${app.password-reset.token-expiration:600}") int tokenExpiration) {
        this.redisTemplate = redisTemplate;
        this.tokenExpiration = tokenExpiration;
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_PREFIX + tokenHash;
    }

    private String userKey(SubjectType subjectType, Long userId) {
        return USER_PREFIX + subjectType.name() + ":" + userId;
    }

    // ==================== 工具方法 ====================

    /**
     * 生成随机Token（原始值，返回给用户）
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 对Token进行SHA-256哈希（Redis不存明文）
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    // ==================== 核心方法 ====================

    /**
     * 生成密码重置Token
     *
     * @param userId   用户ID
     * @param userType 用户类型（PROMOTION）
     * @return 原始Token（返回给用户，仅此一次可见）
     */
    @Override
    public String generateResetToken(Long userId, SubjectType subjectType) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        String payload = subjectType.name() + ":" + userId;

        try {
            redisTemplate.execute(
                    ISSUE_SCRIPT,
                    List.of(userKey(subjectType, userId)),
                    TOKEN_PREFIX, tokenHash, "READY|" + payload, payload, String.valueOf(tokenExpiration));
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }

        return rawToken;
    }

    /**
     * 验证并消费重置Token（一次性使用）
     *
     * @param rawToken 原始Token
     * @return 用户ID
     */
    @Override
    public PasswordResetTokenReservation reserveToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        String payload;
        try {
            payload = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(tokenKey(tokenHash)),
                    USER_PREFIX,
                    tokenHash);
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
        if (payload == null) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        int separator = payload.indexOf(':');
        if (separator <= 0 || separator == payload.length() - 1) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        String subjectValue = payload.substring(0, separator);
        String userIdValue = payload.substring(separator + 1);
        try {
            return new PasswordResetTokenReservation(
                    Long.parseLong(userIdValue), SubjectType.valueOf(subjectValue), tokenHash);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
    }

    @Override
    public void completeToken(PasswordResetTokenReservation reservation) {
        String payload = reservation.subjectType().name() + ":" + reservation.userId();
        try {
            redisTemplate.execute(
                    COMPLETE_SCRIPT,
                    List.of(tokenKey(reservation.tokenHash()),
                            userKey(reservation.subjectType(), reservation.userId())),
                    payload,
                    reservation.tokenHash());
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }

    @Override
    public void restoreReady(PasswordResetTokenReservation reservation) {
        String payload = reservation.subjectType().name() + ":" + reservation.userId();
        try {
            redisTemplate.execute(
                    RESTORE_SCRIPT,
                    List.of(tokenKey(reservation.tokenHash()),
                            userKey(reservation.subjectType(), reservation.userId())),
                    payload,
                    reservation.tokenHash());
        } catch (DataAccessException exception) {
            throw new AuthStateUnavailableException(exception);
        }
    }
}
