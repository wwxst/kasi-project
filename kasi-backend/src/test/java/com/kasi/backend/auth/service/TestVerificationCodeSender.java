package com.kasi.backend.auth.service;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境捕获验证码，不输出敏感信息。 */
@Component
@Primary
@Profile("test")
public class TestVerificationCodeSender implements VerificationCodeSender {

    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @Override
    public void send(String target, String targetType, String code) {
        codes.put(target, code);
    }

    public String latestCode(String target) {
        return codes.get(target);
    }

    public void clear() {
        codes.clear();
    }
}
