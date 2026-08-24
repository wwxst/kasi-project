package com.kasi.backend.provider.exception;

public class ProviderTransientException extends RuntimeException {
    public ProviderTransientException(String message) {
        super(message);
    }
}
