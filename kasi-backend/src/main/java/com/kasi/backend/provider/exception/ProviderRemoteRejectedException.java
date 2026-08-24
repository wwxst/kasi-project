package com.kasi.backend.provider.exception;

public class ProviderRemoteRejectedException extends RuntimeException {
    public ProviderRemoteRejectedException(String message) {
        super(message);
    }
}
