package com.kasi.backend.common.crypto;

import com.kasi.backend.common.config.CredentialProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("通用凭据加密")
class CredentialCipherTest {

    private static final String TEST_MASTER_KEY = Base64.getEncoder().encodeToString(
            "test-provider-key-material-32byt".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_MASTER_KEY = Base64.getEncoder().encodeToString(
            "other-provider-key-material-32by".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("同一密钥每次加密产生不同密文且均可解密")
    void encryptUsesRandomIvAndDecrypts() {
        CredentialCipher cipher = cipher(TEST_MASTER_KEY);

        String first = cipher.encrypt("shared-secret");
        String second = cipher.encrypt("shared-secret");

        assertThat(first).startsWith("v1:").doesNotContain("shared-secret");
        assertThat(second).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("shared-secret");
        assertThat(cipher.decrypt(second)).isEqualTo("shared-secret");
    }

    @Test
    @DisplayName("错误主密钥无法解密且异常不泄露密文")
    void decryptWithWrongMasterKeyFailsWithoutLeakingCiphertext() {
        String ciphertext = cipher(TEST_MASTER_KEY).encrypt("shared-secret");

        assertDecryptFailure(cipher(OTHER_MASTER_KEY), ciphertext);
    }

    @Test
    @DisplayName("未知密文版本无法解密")
    void decryptRejectsUnknownVersion() {
        assertDecryptFailure(cipher(TEST_MASTER_KEY), "v2:AAAA");
    }

    @Test
    @DisplayName("格式错误或过短的密文无法解密")
    void decryptRejectsMalformedCiphertext() {
        CredentialCipher cipher = cipher(TEST_MASTER_KEY);

        assertDecryptFailure(cipher, "plain-text");
        assertDecryptFailure(cipher, "v1:not-base64");
        assertDecryptFailure(cipher, "v1:" + Base64.getEncoder().encodeToString(new byte[27]));
    }

    @Test
    @DisplayName("被篡改的密文无法解密且异常不泄露密文")
    void decryptRejectsTamperedCiphertextWithoutLeakingCiphertext() {
        CredentialCipher cipher = cipher(TEST_MASTER_KEY);
        String ciphertext = cipher.encrypt("shared-secret");
        byte[] payload = Base64.getDecoder().decode(ciphertext.substring("v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertDecryptFailure(cipher, tampered);
    }

    @Test
    @DisplayName("空白凭据不能加密")
    void encryptRejectsBlankPlaintext() {
        assertThatThrownBy(() -> cipher(TEST_MASTER_KEY).encrypt("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("凭据不能为空");
    }

    @Test
    @DisplayName("主密钥必须是Base64编码的32字节值")
    void propertiesRejectInvalidMasterKeys() {
        assertThatThrownBy(() -> properties("not-base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("凭据主密钥必须是Base64编码的32字节密钥")
                .hasMessageNotContaining("not-base64");

        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> properties(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("凭据主密钥必须是Base64编码的32字节密钥")
                .hasMessageNotContaining(shortKey);
    }

    private CredentialCipher cipher(String masterKey) {
        return new AesGcmCredentialCipher(properties(masterKey));
    }

    private CredentialProperties properties(String masterKey) {
        CredentialProperties properties = new CredentialProperties();
        properties.setMasterKey(masterKey);
        return properties;
    }

    private void assertDecryptFailure(CredentialCipher cipher, String ciphertext) {
        assertThatThrownBy(() -> cipher.decrypt(ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("凭据无法解密")
                .hasMessageNotContaining(ciphertext);
    }
}
