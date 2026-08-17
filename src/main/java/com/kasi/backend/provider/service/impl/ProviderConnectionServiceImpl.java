package com.kasi.backend.provider.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.service.ProviderConnectionService;
import com.kasi.backend.provider.service.ProviderCredentialCipher;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProviderConnectionServiceImpl implements ProviderConnectionService {

    private final ShortDramaProviderMapper providerMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderCredentialCipher credentialCipher;
    private final List<ProviderAdapter> providerAdapters;

    public ProviderConnectionServiceImpl(ShortDramaProviderMapper providerMapper,
                                         ShortDramaConnectionMapper connectionMapper,
                                         ProviderCredentialCipher credentialCipher,
                                         List<ProviderAdapter> providerAdapters) {
        this.providerMapper = providerMapper;
        this.connectionMapper = connectionMapper;
        this.credentialCipher = credentialCipher;
        this.providerAdapters = List.copyOf(providerAdapters);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderVO> getProviders() {
        return providerMapper.findAll().stream()
                .map(provider -> toProviderVO(provider, connectionMapper.findByProviderId(provider.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ProviderConnectionVO upsert(Long operatorId, Long providerId, UpsertProviderConnectionDTO request) {
        ShortDramaProvider provider = providerMapper.findById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }

        ShortDramaConnection existing = connectionMapper.findByProviderId(providerId);
        String apiKey = trimToNull(request.getApiKey());
        if (existing == null && apiKey == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }

        ShortDramaConnection connection = buildConnection(
                existing, operatorId, providerId, request, apiKey);
        int affected = existing == null
                ? connectionMapper.insert(connection)
                : connectionMapper.update(connection);
        if (affected != 1) {
            throw new IllegalStateException("平台接入账号保存未生效");
        }

        ShortDramaConnection saved = connectionMapper.findByProviderId(providerId);
        if (saved == null) {
            throw new IllegalStateException("平台接入账号保存后无法读取");
        }
        return toConnectionVO(saved);
    }

    @Override
    public ProviderConnectionTestVO testConnection(Long providerId) {
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
                || trimToNull(connection.getPartnerId()) == null
                || trimToNull(connection.getApiKeyCiphertext()) == null
                || trimToNull(connection.getCurrency()) == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }

        String apiKey;
        try {
            apiKey = credentialCipher.decrypt(connection.getApiKeyCiphertext());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.PROVIDER_CREDENTIAL_UNAVAILABLE);
        }
        ProviderAdapter adapter = providerAdapters.stream()
                .filter(candidate -> provider.getProviderCode().equals(candidate.providerCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID));
        return adapter.testConnection(new ProviderConnectionSecret(
                connection.getPartnerId(), apiKey, connection.getCurrency()));
    }

    private ShortDramaConnection buildConnection(ShortDramaConnection existing, Long operatorId,
                                                   Long providerId, UpsertProviderConnectionDTO request,
                                                   String apiKey) {
        ShortDramaConnection connection = new ShortDramaConnection();
        if (existing != null) {
            connection.setId(existing.getId());
        }
        connection.setProviderId(providerId);
        connection.setConnectionName(request.getConnectionName().trim());
        connection.setPartnerId(request.getPartnerId().trim());
        connection.setApiKeyCiphertext(apiKey == null ? null : credentialCipher.encrypt(apiKey));
        connection.setCurrency(request.getCurrency().trim().toUpperCase(Locale.ROOT));
        connection.setStatus(request.getStatus());
        connection.setUpdatedBy(operatorId);
        if (existing == null) {
            connection.setCreatedBy(operatorId);
        }
        return connection;
    }

    private ProviderVO toProviderVO(ShortDramaProvider provider, ShortDramaConnection connection) {
        return ProviderVO.builder()
                .id(provider.getId())
                .providerCode(provider.getProviderCode())
                .providerName(provider.getProviderName())
                .status(provider.getStatus())
                .capabilities(capabilitiesFor(provider.getProviderCode()))
                .connection(connection == null ? null : toConnectionVO(connection))
                .build();
    }

    private Set<ProviderCapability> capabilitiesFor(String providerCode) {
        return providerAdapters.stream()
                .filter(adapter -> providerCode.equals(adapter.providerCode()))
                .findFirst()
                .map(ProviderAdapter::capabilities)
                .map(Set::copyOf)
                .orElseGet(Set::of);
    }

    private ProviderConnectionVO toConnectionVO(ShortDramaConnection connection) {
        return ProviderConnectionVO.builder()
                .id(connection.getId())
                .connectionName(connection.getConnectionName())
                .partnerId(connection.getPartnerId())
                .currency(connection.getCurrency())
                .status(connection.getStatus())
                .credentialConfigured(trimToNull(connection.getApiKeyCiphertext()) != null)
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
