package com.kasi.backend.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminListItemVO {

    private Long id;
    private String username;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Long departmentId;
    private Integer status;
    private Integer isSuperAdmin;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
