package com.kasi.backend.sms.entity;

public record SmsRuntimeConfig(
        String accessKeyId,
        String accessKeySecret,
        String signName,
        String templateCode) {
}
