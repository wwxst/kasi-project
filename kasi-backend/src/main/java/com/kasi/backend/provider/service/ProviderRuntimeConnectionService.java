package com.kasi.backend.provider.service;

import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;

import java.util.List;

public interface ProviderRuntimeConnectionService {
    ProviderRuntimeConnection resolve(Long providerId, ProviderCapability capability);

    List<ProviderRuntimeConnection> resolveAll(ProviderCapability capability);
}
