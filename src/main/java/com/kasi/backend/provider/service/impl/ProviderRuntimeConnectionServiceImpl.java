package com.kasi.backend.provider.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.service.ProviderCredentialCipher;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderAdapterRegistry;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderRuntimeConnectionServiceImpl implements ProviderRuntimeConnectionService {

    private final ShortDramaProviderMapper providerMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderCredentialCipher credentialCipher;
    private final ProviderAdapterRegistry adapterRegistry;

    @Override
    @Transactional(readOnly = true)
    public ProviderRuntimeConnection resolve(Long providerId, ProviderCapability capability) {
        ShortDramaProvider provider = providerMapper.findById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(provider.getStatus())) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(connection.getStatus())
                || blank(connection.getBaseUrl()) || blank(connection.getPartnerId())
                || blank(connection.getApiKeyCiphertext())) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }
        ProviderAdapter adapter = adapterRegistry.require(provider.getProviderCode());
        if (!adapter.capabilities().contains(capability)) {
            throw new BusinessException(ErrorCode.PROVIDER_CAPABILITY_UNSUPPORTED);
        }
        String apiKey;
        try {
            apiKey = credentialCipher.decrypt(connection.getApiKeyCiphertext());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_CREDENTIAL_UNAVAILABLE);
        }
        ProviderConnectionSecret secret = new ProviderConnectionSecret(
                connection.getBaseUrl(), connection.getPartnerId(), apiKey, connection.getCurrency());
        return new ProviderRuntimeConnection(connection.getId(), provider.getId(), provider.getProviderCode(),
                provider.getProviderName(), secret, adapter);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
