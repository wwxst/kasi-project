package com.kasi.backend.auth.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 控制台验证码发送器（开发环境）
 * <p>
 * 仅将验证码输出到日志，不实际发送短信或邮件。
 */
@Slf4j
@Component
@Profile("local")
public class ConsoleVerificationCodeSender implements VerificationCodeSender {

    @Override
    public void send(String target, String targetType, String code) {
        log.info("========== 验证码发送 ==========");
        log.info("目标类型: {}", targetType);
        log.info("发送目标: {}", target);
        log.info("验证码: {}", code);
        log.info("================================");
    }
}
