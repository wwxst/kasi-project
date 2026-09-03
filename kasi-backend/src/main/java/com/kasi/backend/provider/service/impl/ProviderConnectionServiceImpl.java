package com.kasi.backend.provider.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.dto.UpdateProviderFilingModeDTO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.enums.FilingMode;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.service.ProviderConnectionService;
import com.kasi.backend.common.crypto.CredentialCipher;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderVO;
import com.kasi.backend.provider.vo.ProviderFilingModeVO;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProviderConnectionServiceImpl implements ProviderConnectionService {

    private final ShortDramaProviderMapper providerMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final CredentialCipher credentialCipher;
    private final List<ProviderAdapter> providerAdapters;
    private final ProviderMediaFilingMapper filingMapper;

    public ProviderConnectionServiceImpl(ShortDramaProviderMapper providerMapper,
                                         ShortDramaConnectionMapper connectionMapper,
                                         CredentialCipher credentialCipher,
                                         List<ProviderAdapter> providerAdapters) {
        this(providerMapper, connectionMapper, credentialCipher, providerAdapters, null);
    }

    @Autowired
    public ProviderConnectionServiceImpl(ShortDramaProviderMapper providerMapper,
                                         ShortDramaConnectionMapper connectionMapper,
                                         CredentialCipher credentialCipher,
                                         List<ProviderAdapter> providerAdapters,
                                         ProviderMediaFilingMapper filingMapper) {
        this.providerMapper = providerMapper;
        this.connectionMapper = connectionMapper;
        this.credentialCipher = credentialCipher;
        this.providerAdapters = List.copyOf(providerAdapters);
        this.filingMapper = filingMapper;
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
        FilingMode targetMode = request.getFilingMode() != null
                ? request.getFilingMode()
                : existing == null ? FilingMode.API : existing.getFilingMode();
        String baseUrl = trimToNull(request.getBaseUrl());
        String partnerId = trimToNull(request.getPartnerId());
        String mediaRootDomain = trimToNull(request.getMediaRootDomain());
        if (targetMode == FilingMode.API && (baseUrl == null || partnerId == null
                || mediaRootDomain == null
                || (existing == null && apiKey == null))) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }

        ShortDramaConnection connection = buildConnection(
                existing, operatorId, providerId, provider.getProviderName(), request, apiKey, targetMode);
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
        FilingMode currentMode = existing == null ? FilingMode.API : existing.getFilingMode();
        if (currentMode != FilingMode.MANUAL && targetMode == FilingMode.MANUAL
                && existing != null && filingMapper != null) {
            filingMapper.stopPendingTasksByConnectionId(existing.getId());
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
                || trimToNull(connection.getBaseUrl()) == null
                || trimToNull(connection.getMediaRootDomain()) == null
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
                connection.getBaseUrl(), connection.getPartnerId(), apiKey, connection.getCurrency()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderFilingModeVO getFilingMode(Long providerId) {
        ShortDramaProvider provider = providerMapper.findById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        }
        return toFilingModeVO(provider, connection);
    }

    @Override
    @Transactional
    public ProviderFilingModeVO updateFilingMode(Long operatorId, Long providerId,
                                                  UpdateProviderFilingModeDTO request) {
        ShortDramaProvider provider = providerMapper.findById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        }
        FilingMode targetMode = request.getFilingMode();
        if (targetMode == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        FilingMode currentMode = connection.getFilingMode();
        int affected = connectionMapper.updateFilingMode(connection.getId(), targetMode, operatorId);
        if (affected != 1) {
            throw new IllegalStateException("平台报白方式保存失败");
        }
        connection.setFilingMode(targetMode);
        connection.setUpdatedBy(operatorId);
        if (currentMode != FilingMode.MANUAL && targetMode == FilingMode.MANUAL && filingMapper != null) {
            filingMapper.stopPendingTasksByConnectionId(connection.getId());
        }
        return toFilingModeVO(provider, connection);
    }

    private ShortDramaConnection buildConnection(ShortDramaConnection existing, Long operatorId,
                                                   Long providerId, String providerName,
                                                   UpsertProviderConnectionDTO request,
                                                   String apiKey, FilingMode filingMode) {
        ShortDramaConnection connection = new ShortDramaConnection();
        if (existing != null) {
            connection.setId(existing.getId());
        }
        connection.setProviderId(providerId);
        connection.setConnectionName(defaultText(request.getConnectionName(), providerName));
        connection.setBaseUrl(trimToNull(request.getBaseUrl()));
        String mediaRootDomain = trimToNull(request.getMediaRootDomain());
        connection.setMediaRootDomain(mediaRootDomain == null ? null : mediaRootDomain.toLowerCase(Locale.ROOT));
        connection.setPartnerId(trimToNull(request.getPartnerId()));
        connection.setApiKeyCiphertext(apiKey == null ? null : credentialCipher.encrypt(apiKey));
        connection.setCurrency(defaultText(request.getCurrency(), "USD").toUpperCase(Locale.ROOT));
        connection.setFilingMode(filingMode);
        connection.setStatus(request.getStatus());
        connection.setUpdatedBy(operatorId);
        if (existing == null) {
            connection.setCreatedBy(operatorId);
        }
        return connection;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
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
                .mediaRootDomain(connection.getMediaRootDomain())
                .baseUrl(connection.getBaseUrl())
                .partnerId(connection.getPartnerId())
                .currency(connection.getCurrency())
                .status(connection.getStatus())
                .filingMode(connection.getFilingMode())
                .credentialConfigured(trimToNull(connection.getApiKeyCiphertext()) != null)
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .build();
    }

    private ProviderFilingModeVO toFilingModeVO(ShortDramaProvider provider, ShortDramaConnection connection) {
        return ProviderFilingModeVO.builder()
                .providerId(provider.getId())
                .providerName(provider.getProviderName())
                .filingMode(connection.getFilingMode())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
