package com.kasi.backend.admin.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理员实体
 */
@Data
public class SysAdminUser {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Long departmentId;
    private Integer status;
    private Integer isSuperAdmin;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime passwordChangedAt;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
