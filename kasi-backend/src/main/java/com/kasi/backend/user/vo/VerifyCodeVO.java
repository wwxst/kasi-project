package com.kasi.backend.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 验证码验证通过后的响应（包含重置Token）
 */
@Data
@Builder
public class VerifyCodeVO {

    private String resetToken;
    private Long expiresIn;
}
