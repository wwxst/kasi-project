package com.kasi.backend.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推广用户实体
 */
@Data
public class PromotionUser {

    private Long id;
    private String userNo;
    private String password;
    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Integer status;
    private String registerSource;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
