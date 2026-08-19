package com.kasi.backend.provider.service;

import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;

public interface ProviderRuntimeConnectionService {
    ProviderRuntimeConnection resolve(Long providerId, ProviderCapability capability);
}
