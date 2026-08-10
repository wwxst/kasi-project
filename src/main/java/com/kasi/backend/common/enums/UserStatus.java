package com.kasi.backend.common.enums;

import lombok.Getter;

/**
 * 用户/管理员状态
 */
@Getter
public enum UserStatus {
    DISABLED(0, "禁用"),
    NORMAL(1, "正常");

    private final int code;
    private final String desc;

    UserStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
