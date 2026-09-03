package com.kasi.backend.common.crypto;

import com.kasi.backend.common.config.CredentialProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class AesGcmCredentialCipher implements CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int TAG_LENGTH_BITS = TAG_LENGTH_BYTES * Byte.SIZE;
    private static final String DECRYPTION_FAILURE_MESSAGE = "凭据无法解密";

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCredentialCipher(CredentialProperties properties) {
        this.masterKey = new SecretKeySpec(properties.getMasterKey(), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("凭据不能为空");
        }

        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据无法加密", exception);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith(VERSION_PREFIX)) {
                throw new IllegalArgumentException();
            }
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION_PREFIX.length()));
            if (payload.length < IV_LENGTH + TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException();
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(DECRYPTION_FAILURE_MESSAGE);
        }
    }
}
