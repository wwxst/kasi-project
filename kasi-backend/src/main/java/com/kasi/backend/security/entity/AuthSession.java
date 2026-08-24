package com.kasi.backend.security.entity;

/** 登录成功后写入 Redis 的单会话标识。 */
public record AuthSession(String jti, String sessionVersion) {
}
