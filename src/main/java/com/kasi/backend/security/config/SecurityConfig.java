package com.kasi.backend.security.config;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.token.JwtAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 统一配置
 * <p>
 * 采用无状态JWT认证模式，关闭Session和表单登录，
 * 所有认证失败和权限不足均返回JSON而非重定向HTML页面。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 关闭CSRF（REST API无需CSRF保护）
                .csrf(csrf -> csrf.disable())
                // 无状态会话
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 异常处理：返回JSON
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error(1002, "未登录或Token已过期")
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    ApiResponse.error(1003, "无权限访问")
                            ));
                        })
                )
                // 授权规则
                .authorizeHttpRequests(auth -> auth
                        // 匿名接口
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .requestMatchers("/api/user/auth/login").permitAll()
                        .requestMatchers("/api/user/auth/register").permitAll()
                        .requestMatchers("/api/user/auth/password/forgot/**").permitAll()
                        .requestMatchers("/api/user/auth/password/reset").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 管理员接口：仅ADMIN角色可访问
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 用户接口：仅USER角色可访问
                        .requestMatchers("/api/user/**").hasRole("USER")
                        // 其他接口需要认证
                        .anyRequest().authenticated()
                )
                // 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
