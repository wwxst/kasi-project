package com.kasi.backend.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "app.provider-credentials")
public class ProviderCredentialProperties {

    private static final int AES_256_KEY_LENGTH = 32;
    private static final String INVALID_MASTER_KEY_MESSAGE = "平台密钥主密钥必须是Base64编码的32字节密钥";

    private byte[] masterKey;

    public void setMasterKey(String masterKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(masterKey);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException(INVALID_MASTER_KEY_MESSAGE);
        }
        if (decoded.length != AES_256_KEY_LENGTH) {
            throw new IllegalArgumentException(INVALID_MASTER_KEY_MESSAGE);
        }
        this.masterKey = decoded;
    }

    public byte[] getMasterKey() {
        if (masterKey == null) {
            throw new IllegalArgumentException(INVALID_MASTER_KEY_MESSAGE);
        }
        return masterKey.clone();
    }
}
