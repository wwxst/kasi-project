package com.kasi.backend.security.service.impl;

import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.context.AuthContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token服务，负责Token的生成、解析和验证
 */
@Slf4j
@Component
public class TokenServiceImpl implements TokenService {

    private final SecretKey secretKey;
    private final long expiration;

    public TokenServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    @Override
    public String generateToken(Long subjectId, SubjectType subjectType, String username,
                                String jti, String sessionVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .id(jti)
                .claim("subjectType", subjectType.name())
                .claim("username", username)
                .claim("sessionVersion", sessionVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从Token中解析认证上下文
     *
     * @param token JWT Token
     * @return 认证上下文，解析失败返回null
     */
    @Override
    public AuthContext parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return AuthContext.builder()
                    .subjectId(Long.parseLong(claims.getSubject()))
                    .subjectType(SubjectType.valueOf(claims.get("subjectType", String.class)))
                    .username(claims.get("username", String.class))
                    .jti(claims.getId())
                    .sessionVersion(claims.get("sessionVersion", String.class))
                    .build();
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
            return null;
        } catch (SecurityException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            log.debug("Token无效: {}", e.getMessage());
            return null;
        }
    }

}
