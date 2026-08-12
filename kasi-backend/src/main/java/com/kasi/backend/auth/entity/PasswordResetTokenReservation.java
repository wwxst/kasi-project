package com.kasi.backend.auth.entity;

import com.kasi.backend.common.enums.SubjectType;

/** 已从READY原子预占为PROCESSING的密码重置凭证。 */
public record PasswordResetTokenReservation(
        Long userId,
        SubjectType subjectType,
        String tokenHash
) {
}
