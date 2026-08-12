package com.kasi.backend.security.session;

import com.kasi.backend.common.enums.SubjectType;

/** 关键账号状态修改期间的 Redis 会话版本占位。 */
public record SessionMutation(SubjectType subjectType, Long subjectId, String nonce) {
}
