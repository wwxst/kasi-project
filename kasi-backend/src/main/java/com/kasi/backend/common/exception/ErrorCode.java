package com.kasi.backend.common.exception;

import lombok.Getter;

/**
 * 统一错误码枚举
 * <p>
 * 错误码规则：
 * 1xxx - 通用错误
 * 2xxx - 管理员认证错误
 * 3xxx - 用户认证错误
 * 4xxx - 验证码错误
 * 5xxx - 密码重置错误
 */
@Getter
public enum ErrorCode {

    // ========== 通用错误 ==========
    SUCCESS(0, "成功"),
    BAD_REQUEST(1001, "请求参数错误"),
    UNAUTHORIZED(1002, "未登录或Token已过期"),
    FORBIDDEN(1003, "无权限访问"),
    NOT_FOUND(1004, "资源不存在"),
    INTERNAL_ERROR(1005, "服务器内部错误"),
    VALIDATION_ERROR(1006, "参数校验失败"),

    // ========== 管理员认证错误 ==========
    ADMIN_NOT_FOUND(2001, "账号或密码错误"),
    ADMIN_DISABLED(2002, "账号已被禁用"),
    ADMIN_PASSWORD_ERROR(2003, "账号或密码错误"),
    ADMIN_OLD_PASSWORD_ERROR(2004, "原密码错误"),
    ADMIN_NEW_PASSWORD_SAME(2005, "新密码不能与旧密码相同"),

    // ========== 用户认证错误 ==========
    USER_NOT_FOUND(3001, "账号或密码错误"),
    USER_DISABLED(3002, "账号已被禁用"),
    USER_PASSWORD_ERROR(3003, "账号或密码错误"),
    USER_OLD_PASSWORD_ERROR(3004, "原密码错误"),
    USER_NEW_PASSWORD_SAME(3005, "新密码不能与旧密码相同"),
    USER_MOBILE_DUPLICATE(3006, "该手机号已注册"),
    USER_EMAIL_DUPLICATE(3007, "该邮箱已注册"),
    USER_USERNAME_DUPLICATE(3008, "该用户名已注册"),
    USER_ACCOUNT_REQUIRED(3009, "手机号或邮箱不能同时为空"),
    USER_PASSWORD_NOT_MATCH(3010, "两次输入的密码不一致"),

    // ========== 验证码错误 ==========
    VERIFICATION_CODE_ERROR(4001, "验证码错误"),
    VERIFICATION_CODE_EXPIRED(4002, "验证码已过期"),
    VERIFICATION_CODE_TOO_FREQUENT(4003, "验证码发送过于频繁，请稍后再试"),
    VERIFICATION_CODE_DAILY_LIMIT(4004, "今日验证码发送次数已达上限"),
    VERIFICATION_CODE_ALREADY_USED(4005, "验证码已被使用"),

    // ========== 密码重置错误 ==========
    RESET_TOKEN_INVALID(5001, "重置凭证无效"),
    RESET_TOKEN_EXPIRED(5002, "重置凭证已过期"),
    RESET_TOKEN_ALREADY_USED(5003, "重置凭证已被使用"),

    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
