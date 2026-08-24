package com.kasi.backend.provider.service;

public interface ProviderCredentialCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
