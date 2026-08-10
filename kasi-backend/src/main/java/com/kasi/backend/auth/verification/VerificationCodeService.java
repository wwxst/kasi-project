package com.kasi.backend.auth.verification;

import com.kasi.backend.common.enums.TargetType;
import com.kasi.backend.common.enums.VerificationScene;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务（Redis版）
 * <p>
 * 使用Redis替代MySQL存储验证码，利用TTL机制自动过期，无需手动清理。
 * <p>
 * Redis Key设计：
 * <ul>
 *   <li>vc:code:{target}:{scene} — 验证码哈希值，TTL=codeExpiration秒</li>
 *   <li>vc:cooldown:{target}:{scene} — 发送冷却标记，TTL=resendInterval秒</li>
 *   <li>vc:daily:{target}:{scene}:{yyyyMMdd} — 当日发送计数，过期至午夜</li>
 *   <li>vc:fail:{target}:{scene} — 连续验证失败计数，TTL=codeExpiration秒</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final VerificationCodeSender verificationCodeSender;

    @Value("${app.verification-code.expiration:300}")
    private int codeExpiration;

    @Value("${app.verification-code.resend-interval:60}")
    private int resendInterval;

    @Value("${app.verification-code.daily-limit:10}")
    private int dailyLimit;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    // ==================== Key构建 ====================

    private String codeKey(String target, VerificationScene scene) {
        return "vc:code:" + target + ":" + scene.name();
    }

    private String cooldownKey(String target, VerificationScene scene) {
        return "vc:cooldown:" + target + ":" + scene.name();
    }

    private String dailyKey(String target, VerificationScene scene) {
        return "vc:daily:" + target + ":" + scene.name() + ":" + LocalDate.now().format(DATE_FMT);
    }

    private String failKey(String target, VerificationScene scene) {
        return "vc:fail:" + target + ":" + scene.name();
    }

    // ==================== 工具方法 ====================

    /**
     * 生成6位数字验证码
     */
    private String generateCode() {
        int code = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * 对验证码进行SHA-256哈希（数据库/Redis永不存明文）
     */
    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    /**
     * 判断目标类型（手机号或邮箱）
     */
    private TargetType detectTargetType(String target) {
        return target.contains("@") ? TargetType.EMAIL : TargetType.MOBILE;
    }

    /**
     * 计算到午夜的剩余秒数
     */
    private long secondsUntilMidnight() {
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(LocalDateTime.now(), midnight).getSeconds();
    }

    // ==================== 核心方法 ====================

    /**
     * 发送验证码
     *
     * @param target 手机号或邮箱
     * @param scene  使用场景
     */
    public void sendVerificationCode(String target, VerificationScene scene) {
        // 1. 检查发送冷却（60秒内不可重复发送）
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey(target, scene), "1", Duration.ofSeconds(resendInterval));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_TOO_FREQUENT);
        }

        // 2. 检查当日发送次数上限
        String dailyKey = dailyKey(target, scene);
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            // 首次递增，设置过期到当天午夜
            redisTemplate.expire(dailyKey, Duration.ofSeconds(secondsUntilMidnight()));
        }
        if (dailyCount != null && dailyCount > dailyLimit) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_DAILY_LIMIT);
        }

        // 3. 生成验证码并存储（仅存哈希）
        String code = generateCode();
        String codeHash = hashCode(code);
        redisTemplate.opsForValue()
                .set(codeKey(target, scene), codeHash, Duration.ofSeconds(codeExpiration));

        // 4. 失败计数重置
        redisTemplate.delete(failKey(target, scene));

        // 5. 发送验证码
        TargetType targetType = detectTargetType(target);
        verificationCodeSender.send(target, targetType.name(), code);
    }

    /**
     * 验证验证码
     *
     * @param target 手机号或邮箱
     * @param scene  使用场景
     * @param code   用户输入的验证码
     * @return 验证成功返回true
     */
    public boolean verifyCode(String target, VerificationScene scene, String code) {
        String storedHash = redisTemplate.opsForValue().get(codeKey(target, scene));
        if (storedHash == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
        }

        String inputHash = hashCode(code);
        if (!inputHash.equals(storedHash)) {
            // 验证失败，递增失败计数
            String failKey = failKey(target, scene);
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, Duration.ofSeconds(codeExpiration));
            if (failCount != null && failCount >= MAX_FAILED_ATTEMPTS) {
                // 失败次数过多，删除验证码，强制用户重新发送
                redisTemplate.delete(codeKey(target, scene));
                throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR.getCode(), "验证码错误次数过多，请重新发送");
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
        }

        // 验证成功，删除验证码和失败计数（标记为已使用）
        redisTemplate.delete(codeKey(target, scene));
        redisTemplate.delete(failKey(target, scene));
        return true;
    }
}
