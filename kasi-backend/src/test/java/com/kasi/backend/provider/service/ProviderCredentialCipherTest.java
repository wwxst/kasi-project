package com.kasi.backend.provider.service;

import com.kasi.backend.provider.config.ProviderCredentialProperties;
import com.kasi.backend.provider.service.impl.AesGcmProviderCredentialCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCredentialCipherTest {

    private static final String TEST_MASTER_KEY = Base64.getEncoder().encodeToString(
            "test-provider-key-material-32byt".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_MASTER_KEY = Base64.getEncoder().encodeToString(
            "other-provider-key-material-32by".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("同一密钥每次加密产生不同密文且均可解密")
    void encryptUsesRandomIvAndDecrypts() {
        ProviderCredentialCipher cipher = cipher(TEST_MASTER_KEY);

        String first = cipher.encrypt("goodshort-secret");
        String second = cipher.encrypt("goodshort-secret");

        assertThat(first).startsWith("v1:").doesNotContain("goodshort-secret");
        assertThat(second).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("goodshort-secret");
        assertThat(cipher.decrypt(second)).isEqualTo("goodshort-secret");
    }

    @Test
    @DisplayName("错误主密钥无法解密且异常不泄露密文")
    void decryptWithWrongMasterKeyFailsWithoutLeakingCiphertext() {
        String ciphertext = cipher(TEST_MASTER_KEY).encrypt("goodshort-secret");

        assertDecryptFailure(cipher(OTHER_MASTER_KEY), ciphertext);
    }

    @Test
    @DisplayName("未知密文版本无法解密")
    void decryptRejectsUnknownVersion() {
        assertDecryptFailure(cipher(TEST_MASTER_KEY), "v2:AAAA");
    }

    @Test
    @DisplayName("格式错误的密文无法解密")
    void decryptRejectsMalformedCiphertext() {
        ProviderCredentialCipher cipher = cipher(TEST_MASTER_KEY);

        assertDecryptFailure(cipher, "plain-text");
        assertDecryptFailure(cipher, "v1:not-base64");
    }

    @Test
    @DisplayName("短于IV与认证标签的载荷无法解密")
    void decryptRejectsPayloadShorterThanIvAndTag() {
        String shortPayload = Base64.getEncoder().encodeToString(new byte[27]);

        assertDecryptFailure(cipher(TEST_MASTER_KEY), "v1:" + shortPayload);
    }

    @Test
    @DisplayName("被篡改的密文无法解密且异常不泄露密文")
    void decryptRejectsTamperedCiphertextWithoutLeakingCiphertext() {
        ProviderCredentialCipher cipher = cipher(TEST_MASTER_KEY);
        String ciphertext = cipher.encrypt("goodshort-secret");
        byte[] payload = Base64.getDecoder().decode(ciphertext.substring("v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertDecryptFailure(cipher, tampered);
    }

    @Test
    @DisplayName("空白平台密钥不能加密")
    void encryptRejectsBlankPlaintext() {
        ProviderCredentialCipher cipher = cipher(TEST_MASTER_KEY);

        assertThatThrownBy(() -> cipher.encrypt("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("平台密钥不能为空");
    }

    @Test
    @DisplayName("主密钥必须是Base64编码的32字节值")
    void propertiesRejectInvalidMasterKeys() {
        assertThatThrownBy(() -> properties("not-base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("平台密钥主密钥必须是Base64编码的32字节密钥")
                .hasMessageNotContaining("not-base64");

        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> properties(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("平台密钥主密钥必须是Base64编码的32字节密钥")
                .hasMessageNotContaining(shortKey);
    }

    private ProviderCredentialCipher cipher(String masterKey) {
        return new AesGcmProviderCredentialCipher(properties(masterKey));
    }

    private ProviderCredentialProperties properties(String masterKey) {
        ProviderCredentialProperties properties = new ProviderCredentialProperties();
        properties.setMasterKey(masterKey);
        return properties;
    }

    private void assertDecryptFailure(ProviderCredentialCipher cipher, String ciphertext) {
        assertThatThrownBy(() -> cipher.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("平台密钥无法解密")
                .hasMessageNotContaining(ciphertext);
    }
}
