package com.kasi.backend.provider.spi;

import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;

import java.util.Set;

public interface ProviderAdapter {

    String providerCode();

    Set<ProviderCapability> capabilities();

    ProviderConnectionTestVO testConnection(ProviderConnectionSecret connection);
}
