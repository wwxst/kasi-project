package com.kasi.backend.common.exception;

/** Redis认证状态不可用时安全失败。 */
public class AuthStateUnavailableException extends RuntimeException {

    public AuthStateUnavailableException(Throwable cause) {
        super("认证状态服务不可用", cause);
    }
}
