package com.kasi.backend.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前管理员信息响应
 */
@Data
@Builder
public class CurrentAdminVO {

    private Long id;
    private String username;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Integer status;
    private Integer isSuperAdmin;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
}
