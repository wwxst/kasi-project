package com.kasi.backend.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前用户信息响应
 */
@Data
@Builder
public class CurrentUserResponse {

    private Long id;
    private String userNo;
    private String username;
    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
}
