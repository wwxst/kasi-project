package com.kasi.backend.sms.gateway;

public interface SmsGateway {
    SmsSendResult send(SmsSendCommand command);
}
