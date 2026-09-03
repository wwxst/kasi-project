package com.kasi.backend.common.crypto;

public interface CredentialCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
