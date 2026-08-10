package com.kasi.backend.auth.verification;

/**
 * 验证码发送器接口
 * <p>
 * 当前开发环境使用ConsoleVerificationCodeSender（仅输出日志），
 * 后续接入短信/邮件服务时，增加对应实现即可，无需修改业务代码。
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
