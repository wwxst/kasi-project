package com.kasi.backend.security.context;

import com.kasi.backend.common.enums.SubjectType;
import lombok.Builder;
import lombok.Getter;

/**
 * 认证上下文，从JWT Token中解析出的当前登录主体信息
 */
@Getter
@Builder
public class AuthContext {

    /** 主体ID（管理员ID或用户ID） */
    private Long subjectId;
    /** 主体类型：ADMIN / USER */
    private SubjectType subjectType;
    /** 用户名/账号 */
    private String username;
}
