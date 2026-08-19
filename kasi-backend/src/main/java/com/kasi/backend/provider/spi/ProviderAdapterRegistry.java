package com.kasi.backend.provider.spi;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderAdapterRegistry {

    private final Map<String, ProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<ProviderAdapter> adapters) {
        Map<String, ProviderAdapter> indexed = new HashMap<>();
        for (ProviderAdapter adapter : adapters) {
            if (adapter == null || adapter.providerCode() == null || adapter.providerCode().isBlank()) {
                continue;
            }
            if (indexed.putIfAbsent(adapter.providerCode(), adapter) != null) {
                throw new IllegalStateException("短剧平台适配器编码重复: " + adapter.providerCode());
            }
        }
        this.adapters = Map.copyOf(indexed);
    }

    public ProviderAdapter require(String providerCode) {
        ProviderAdapter adapter = adapters.get(providerCode);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CAPABILITY_UNSUPPORTED);
        }
        return adapter;
    }
}
