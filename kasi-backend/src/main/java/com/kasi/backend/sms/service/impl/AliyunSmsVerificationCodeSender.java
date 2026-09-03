package com.kasi.backend.sms.service.impl;

import com.kasi.backend.auth.service.VerificationCodeSender;
import com.kasi.backend.common.enums.TargetType;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.VerificationDeliveryUnavailableException;
import com.kasi.backend.sms.entity.SmsRuntimeConfig;
import com.kasi.backend.sms.gateway.SmsGateway;
import com.kasi.backend.sms.gateway.SmsSendCommand;
import com.kasi.backend.sms.gateway.SmsSendResult;
import com.kasi.backend.sms.service.SmsConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!local & !test")
@RequiredArgsConstructor
public class AliyunSmsVerificationCodeSender implements VerificationCodeSender {
    private final SmsConfigService configService;
    private final SmsGateway gateway;

    @Override
    public void send(String target, TargetType targetType, VerificationScene scene, String code) {
        if (targetType != TargetType.MOBILE) throw new VerificationDeliveryUnavailableException();
        SmsRuntimeConfig config = configService.requireRuntimeConfig(scene);
        SmsSendResult result = gateway.send(new SmsSendCommand(config.accessKeyId(), config.accessKeySecret(),
                target, config.signName(), config.templateCode(), code));
        if (result == null || !"OK".equals(result.code())) throw new VerificationDeliveryUnavailableException();
    }
}
