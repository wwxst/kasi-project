package com.kasi.backend.sms.service.impl;

import com.kasi.backend.auth.service.VerificationCodeSender;
import com.kasi.backend.common.enums.TargetType;
import com.kasi.backend.common.enums.VerificationScene;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Primary
@Profile("!local & !test")
@RequiredArgsConstructor
public class ProductionVerificationCodeSender implements VerificationCodeSender {
    private final AliyunSmsVerificationCodeSender smsSender;
    private final SmtpEmailVerificationCodeSender emailSender;
    @Override public void send(String target, TargetType type, VerificationScene scene, String code) {
        if (type == TargetType.EMAIL) emailSender.send(target, type, scene, code);
        else smsSender.send(target, type, scene, code);
    }
}
