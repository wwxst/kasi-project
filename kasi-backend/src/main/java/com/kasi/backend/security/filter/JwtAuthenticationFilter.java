package com.kasi.backend.security.filter;

import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.enums.UserStatus;
import com.kasi.backend.security.context.AuthContext;
import com.kasi.backend.security.context.AuthContextHolder;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private final SessionService sessionService;
    private final SysAdminUserMapper sysAdminUserMapper;
    private final PromotionUserMapper promotionUserMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token)) {
                AuthContext authContext = tokenService.parseToken(token);
                if (hasRequiredClaims(authContext)
                        && sessionService.isValid(authContext)) {
                    List<SimpleGrantedAuthority> authorities = resolveAuthorities(authContext);
                    if (!authorities.isEmpty()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    authContext, null, authorities
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    AuthContextHolder.set(authContext);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } catch (AuthStateUnavailableException e) {
            writeAuthStateUnavailable(response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private boolean hasRequiredClaims(AuthContext context) {
        return context != null
                && context.getSubjectId() != null
                && context.getSubjectType() != null
                && StringUtils.hasText(context.getJti())
                && StringUtils.hasText(context.getSessionVersion());
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(AuthContext context) {
        if (context.getSubjectType() == SubjectType.ADMIN) {
            SysAdminUser admin = sysAdminUserMapper.findById(context.getSubjectId());
            if (admin == null || !Objects.equals(admin.getStatus(), UserStatus.NORMAL.getCode())) {
                return List.of();
            }
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            if (Objects.equals(admin.getIsSuperAdmin(), 1)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            }
            return authorities;
        }
        if (context.getSubjectType() == SubjectType.USER) {
            PromotionUser user = promotionUserMapper.findById(context.getSubjectId());
            if (user != null && Objects.equals(user.getStatus(), UserStatus.NORMAL.getCode())) {
                return List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }
        }
        return List.of();
    }

    private void writeAuthStateUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.AUTH_STATE_UNAVAILABLE.getCode(),
                        ErrorCode.AUTH_STATE_UNAVAILABLE.getMessage())));
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
