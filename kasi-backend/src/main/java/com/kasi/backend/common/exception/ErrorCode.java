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
 * 6xxx - 短剧平台接入错误
 */
@Getter
public enum ErrorCode {

    // ========== 通用错误 ==========
    UNAUTHORIZED(1002, "未登录或Token已过期"),
    FORBIDDEN(1003, "无权限访问"),
    INTERNAL_ERROR(1005, "服务器内部错误"),
    VALIDATION_ERROR(1006, "参数校验失败"),
    AUTH_STATE_UNAVAILABLE(1007, "认证状态服务不可用"),

    // ========== 管理员认证错误 ==========
    ADMIN_NOT_FOUND(2001, "账号或密码错误"),
    ADMIN_DISABLED(2002, "账号已被禁用"),
    ADMIN_PASSWORD_ERROR(2003, "账号或密码错误"),
    ADMIN_NEW_PASSWORD_SAME(2005, "新密码不能与旧密码相同"),
    ADMIN_MANAGEMENT_NOT_FOUND(2006, "管理员不存在"),
    ADMIN_USERNAME_DUPLICATE(2007, "登录账号已存在"),
    ADMIN_MOBILE_DUPLICATE(2008, "手机号已存在"),
    ADMIN_EMAIL_DUPLICATE(2009, "邮箱已存在"),
    ADMIN_SUPER_ADMIN_PROTECTED(2010, "不允许对超级管理员执行该操作"),
    ADMIN_PASSWORD_NOT_MATCH(2011, "两次输入的密码不一致"),

    // ========== 用户认证错误 ==========
    USER_NOT_FOUND(3001, "账号或密码错误"),
    USER_DISABLED(3002, "账号已被禁用"),
    USER_PASSWORD_ERROR(3003, "账号或密码错误"),
    USER_OLD_PASSWORD_ERROR(3004, "原密码错误"),
    USER_NEW_PASSWORD_SAME(3005, "新密码不能与旧密码相同"),
    USER_MOBILE_DUPLICATE(3006, "该手机号已注册"),
    USER_EMAIL_DUPLICATE(3007, "该邮箱已注册"),
    USER_PASSWORD_NOT_MATCH(3010, "两次输入的密码不一致"),
    USER_MANAGEMENT_NOT_FOUND(3011, "推广用户不存在"),
    USER_CONTACT_REQUIRED(3012, "手机号或邮箱不能同时为空"),
    USER_MANAGEMENT_PASSWORD_NOT_MATCH(3013, "两次输入的密码不一致"),
    USER_MEDIA_ACCOUNT_BOUND(3014, "该推广用户已绑定媒体账号，只能禁用"),

    // ========== 验证码错误 ==========
    VERIFICATION_CODE_ERROR(4001, "验证码错误"),
    VERIFICATION_CODE_TOO_FREQUENT(4003, "验证码发送过于频繁，请稍后再试"),
    VERIFICATION_CODE_DAILY_LIMIT(4004, "今日验证码发送次数已达上限"),

    // ========== 密码重置错误 ==========
    RESET_TOKEN_INVALID(5001, "重置凭证无效"),

    // ========== 短剧平台接入错误 ==========
    PROVIDER_NOT_FOUND(6001, "短剧平台不存在"),
    PROVIDER_CONNECTION_NOT_FOUND(6002, "平台接入账号未配置"),
    PROVIDER_CONNECTION_INVALID(6003, "平台接入账号配置不完整"),
    PROVIDER_CREDENTIAL_UNAVAILABLE(6004, "平台密钥不可用"),
    PROVIDER_REMOTE_UNAVAILABLE(6005, "短剧平台暂时不可用"),
    PROVIDER_REMOTE_REJECTED(6006, "短剧平台拒绝请求"),
    PROVIDER_CAPABILITY_UNSUPPORTED(6007, "短剧平台不支持该能力"),

    // ========== 推广媒体与报备错误 ==========
    MEDIA_ACCOUNT_NOT_FOUND(7001, "媒体账号不存在"),
    MEDIA_ACCOUNT_DUPLICATE(7002, "该媒体账号已被绑定"),
    MEDIA_ACCOUNT_IDENTITY_LOCKED(7003, "已加白账号的平台和账号ID不能修改"),
    MEDIA_ACCOUNT_DISABLED(7004, "媒体账号已停用"),
    MEDIA_TYPE_UNSUPPORTED(7005, "当前平台不支持该媒体类型"),
    MEDIA_FILING_NOT_FOUND(7006, "平台报备不存在"),
    MEDIA_FILING_APPROVED(7007, "已加白报备不需要重试"),
    MEDIA_FILING_STATUS_INVALID(7008, "报备状态只能更新为已加白或已失败"),
    MEDIA_FILING_MANUAL_ONLY(7009, "人工报备无需执行API任务"),

    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
