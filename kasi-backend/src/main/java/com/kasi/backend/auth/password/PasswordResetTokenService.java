package com.kasi.backend.auth.password;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 密码重置Token服务（Redis版）
 * <p>
 * 使用Redis存储重置Token，利用TTL自动过期，无需手动清理。
 * <p>
 * Redis Key: pwd:token:{tokenHash} → userId, TTL=tokenExpiration秒
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.password-reset.token-expiration:600}")
    private int tokenExpiration;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== Key构建 ====================

    private String tokenKey(String tokenHash) {
        return "pwd:token:" + tokenHash;
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
    public String generateResetToken(Long userId, String userType) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        // 存储：tokenHash → userId，TTL自动过期
        redisTemplate.opsForValue()
                .set(tokenKey(tokenHash), String.valueOf(userId), Duration.ofSeconds(tokenExpiration));

        return rawToken;
    }

    /**
     * 验证并消费重置Token（一次性使用）
     *
     * @param rawToken 原始Token
     * @return 用户ID
     */
    public Long validateAndConsumeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        String key = tokenKey(tokenHash);

        // 原子操作：获取并删除Token（GET + DEL，确保一次性消费）
        String userIdStr = redisTemplate.opsForValue().getAndDelete(key);
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }

        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
    }
}
