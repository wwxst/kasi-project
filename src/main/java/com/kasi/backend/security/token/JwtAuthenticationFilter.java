package com.kasi.backend.security.token;

import com.kasi.backend.security.context.AuthContext;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT认证过滤器，从请求头中提取Token并设置Spring Security上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            AuthContext authContext = tokenService.parseToken(token);
            if (authContext != null) {
                String role = "ROLE_" + authContext.getSubjectType().name();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                authContext, null,
                                List.of(new SimpleGrantedAuthority(role))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                AuthContextHolder.set(authContext);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    /**
     * 从请求头提取Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
