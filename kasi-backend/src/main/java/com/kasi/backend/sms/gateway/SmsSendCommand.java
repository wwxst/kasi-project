package com.kasi.backend.sms.gateway;

public record SmsSendCommand(String accessKeyId, String accessKeySecret, String mobile,
                             String signName, String templateCode, String code) {
}
