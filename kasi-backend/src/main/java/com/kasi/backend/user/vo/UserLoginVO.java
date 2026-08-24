package com.kasi.backend.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户登录响应
 */
@Data
@Builder
public class UserLoginVO {

    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private String userNo;
        private String nickname;
        private String mobile;
        private String email;
        private String avatarUrl;
    }
}
