package com.kasi.backend.security.service;

import com.kasi.backend.security.service.impl.TokenServiceImpl;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.context.AuthContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenServiceTest {

    private final TokenService tokenService = new TokenServiceImpl(
            "test-secret-key-for-jwt-testing-must-be-256-bits-long-enough", 7200);

    @Test
    @DisplayName("JWT包含jti和会话版本")
    void generatedTokenContainsSessionClaims() {
        String token = tokenService.generateToken(7L, SubjectType.USER, "user", "jti-1", "version-1");

        AuthContext context = tokenService.parseToken(token);

        assertEquals("jti-1", context.getJti());
        assertEquals("version-1", context.getSessionVersion());
    }
}
