package com.kasi.backend.auth.verification;

/**
 * 验证码发送器接口
 * <p>
 * local profile 使用 ConsoleVerificationCodeSender；test profile 使用测试捕获实现。
 * 生产环境必须提供真实实现，否则应用启动失败。
 */
public interface VerificationCodeSender {

    /**
     * 发送验证码
     *
     * @param target     发送目标（手机号或邮箱）
     * @param targetType 目标类型（MOBILE/EMAIL）
     * @param code       验证码
     */
    void send(String target, String targetType, String code);
}
