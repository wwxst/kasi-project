package com.kasi.backend.common.exception;

public class VerificationDeliveryUnavailableException extends RuntimeException {

    public VerificationDeliveryUnavailableException() {
        super("验证码发送服务不可用");
    }

    public VerificationDeliveryUnavailableException(Throwable cause) {
        super("验证码发送服务不可用", cause);
    }
}
