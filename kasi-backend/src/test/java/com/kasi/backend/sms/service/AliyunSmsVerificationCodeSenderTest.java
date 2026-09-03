package com.kasi.backend.sms.service;

import com.kasi.backend.common.enums.TargetType;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.sms.entity.SmsRuntimeConfig;
import com.kasi.backend.sms.gateway.SmsGateway;
import com.kasi.backend.sms.gateway.SmsSendCommand;
import com.kasi.backend.sms.gateway.SmsSendResult;
import com.kasi.backend.sms.service.SmsConfigService;
import com.kasi.backend.sms.service.impl.AliyunSmsVerificationCodeSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AliyunSmsVerificationCodeSenderTest {
    @Test
    @DisplayName("按场景读取配置并发送短信")
    void sendsConfiguredMobileCode() {
        SmsConfigService config = mock(SmsConfigService.class);
        SmsGateway gateway = mock(SmsGateway.class);
        when(config.requireRuntimeConfig(VerificationScene.REGISTER))
                .thenReturn(new SmsRuntimeConfig("id", "secret", "卡司", "SMS_100"));
        when(gateway.send(any())).thenReturn(new SmsSendResult("OK", "request-1"));
        var sender = new AliyunSmsVerificationCodeSender(config, gateway);

        sender.send("13800138000", TargetType.MOBILE, VerificationScene.REGISTER, "123456");

        verify(gateway).send(new SmsSendCommand("id", "secret", "13800138000", "卡司", "SMS_100", "123456"));
    }

    @Test
    @DisplayName("非手机号目标直接拒绝发送")
    void rejectsEmailTarget() {
        var sender = new AliyunSmsVerificationCodeSender(mock(SmsConfigService.class), mock(SmsGateway.class));
        assertThatThrownBy(() -> sender.send("a@example.com", TargetType.EMAIL,
                VerificationScene.REGISTER, "123456"))
                .isInstanceOf(com.kasi.backend.common.exception.VerificationDeliveryUnavailableException.class);
    }
}
