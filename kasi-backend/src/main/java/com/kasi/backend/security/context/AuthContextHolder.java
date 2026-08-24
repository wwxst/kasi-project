package com.kasi.backend.security.context;

/**
 * 认证上下文持有者，基于ThreadLocal存储当前请求的认证信息
 */
public class AuthContextHolder {

    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    public static void set(AuthContext context) {
        CONTEXT.set(context);
    }

    public static AuthContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前登录管理员ID，若当前主体非管理员则返回null
     */
    public static Long getAdminId() {
        AuthContext ctx = get();
        if (ctx != null && ctx.getSubjectType() == com.kasi.backend.common.enums.SubjectType.ADMIN) {
            return ctx.getSubjectId();
        }
        return null;
    }

    /**
     * 获取当前登录用户ID，若当前主体非普通用户则返回null
     */
    public static Long getUserId() {
        AuthContext ctx = get();
        if (ctx != null && ctx.getSubjectType() == com.kasi.backend.common.enums.SubjectType.USER) {
            return ctx.getSubjectId();
        }
        return null;
    }
}
