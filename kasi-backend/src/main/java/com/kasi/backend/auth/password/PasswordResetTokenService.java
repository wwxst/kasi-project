package com.kasi.backend.auth.password;

import com.kasi.backend.common.enums.SubjectType;

public interface PasswordResetTokenService {

    String generateResetToken(Long userId, SubjectType subjectType);

    PasswordResetTokenReservation reserveToken(String rawToken);

    void completeToken(PasswordResetTokenReservation reservation);

    void restoreReady(PasswordResetTokenReservation reservation);
}
