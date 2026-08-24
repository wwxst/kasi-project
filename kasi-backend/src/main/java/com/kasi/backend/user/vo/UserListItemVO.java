package com.kasi.backend.user.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserListItemVO {
    private Long id;
    private String userNo;
    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Integer status;
    private String registerSource;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
