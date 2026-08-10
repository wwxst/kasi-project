package com.kasi.backend.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员登录响应
 */
@Data
@Builder
public class AdminLoginResponse {

    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private AdminInfo admin;

    @Data
    @Builder
    public static class AdminInfo {
        private Long id;
        private String username;
        private String nickname;
        private String mobile;
        private String email;
        private String avatarUrl;
        private Integer isSuperAdmin;
    }
}
