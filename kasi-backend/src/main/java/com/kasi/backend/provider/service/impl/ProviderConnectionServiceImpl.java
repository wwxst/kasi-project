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
import com.kasi.backend.common.crypto.CredentialCipher;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderVO;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.service.MediaFilingMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.EnumSet;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProviderConnectionServiceImpl implements ProviderConnectionService {

    private final ShortDramaProviderMapper providerMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final CredentialCipher credentialCipher;
    private final List<ProviderAdapter> providerAdapters;
    private final MediaFilingMethodService filingMethodService;
    private final ObjectMapper objectMapper;

    public ProviderConnectionServiceImpl(ShortDramaProviderMapper providerMapper,
                                         ShortDramaConnectionMapper connectionMapper,
                                         CredentialCipher credentialCipher,
                                         List<ProviderAdapter> providerAdapters,
                                         MediaFilingMethodService filingMethodService,
                                         ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.connectionMapper = connectionMapper;
        this.credentialCipher = credentialCipher;
        this.providerAdapters = List.copyOf(providerAdapters);
        this.filingMethodService = filingMethodService;
        this.objectMapper = objectMapper;
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

        ShortDramaConnection existing = connectionMapper.lockByProviderId(providerId);
        List<MediaType> requestedMediaTypes = request.getApiFilingMediaTypes();
        if (existing == null && requestedMediaTypes == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (requestedMediaTypes != null
                && requestedMediaTypes.stream().distinct().count() != requestedMediaTypes.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<MediaType> oldMediaTypes = parseMediaTypes(existing == null ? null : existing.getApiFilingMediaTypes());
        Set<MediaType> newMediaTypes = requestedMediaTypes == null
                ? oldMediaTypes : normalizedMediaTypes(requestedMediaTypes);
        String apiKey = trimToNull(request.getApiKey());
        String baseUrl = normalizeBaseUrl(request.getBaseUrl());
        String partnerId = trimToNull(request.getPartnerId());
        String mediaRootDomain = trimToNull(request.getMediaRootDomain());
        boolean enabled = Integer.valueOf(1).equals(request.getStatus());
        boolean credentialConfigured = apiKey != null
                || existing != null && trimToNull(existing.getApiKeyCiphertext()) != null;
        if (enabled && (baseUrl == null || partnerId == null
                || mediaRootDomain == null || !credentialConfigured)) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }

        ShortDramaConnection connection = buildConnection(
                existing, operatorId, providerId, provider.getProviderName(), request, apiKey);
        connection.setApiFilingMediaTypes(writeMediaTypes(newMediaTypes));
        int affected = existing == null
                ? connectionMapper.insert(connection)
                : connectionMapper.update(connection);
        if (affected != 1) {
            throw new IllegalStateException("平台接入账号保存未生效");
        }
        Set<MediaType> toApi = enumSet(newMediaTypes);
        toApi.removeAll(oldMediaTypes);
        Set<MediaType> toManual = enumSet(oldMediaTypes);
        toManual.removeAll(newMediaTypes);
        filingMethodService.switchMethods(connection.getId(), toApi, toManual);

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

    private ShortDramaConnection buildConnection(ShortDramaConnection existing, Long operatorId,
                                                   Long providerId, String providerName,
                                                   UpsertProviderConnectionDTO request,
                                                   String apiKey) {
        ShortDramaConnection connection = new ShortDramaConnection();
        if (existing != null) {
            connection.setId(existing.getId());
        }
        connection.setProviderId(providerId);
        connection.setConnectionName(defaultText(request.getConnectionName(), providerName));
        connection.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        String mediaRootDomain = trimToNull(request.getMediaRootDomain());
        connection.setMediaRootDomain(mediaRootDomain == null ? null : mediaRootDomain.toLowerCase(Locale.ROOT));
        connection.setPartnerId(trimToNull(request.getPartnerId()));
        connection.setApiKeyCiphertext(apiKey == null ? null : credentialCipher.encrypt(apiKey));
        connection.setCurrency(defaultText(request.getCurrency(), "USD").toUpperCase(Locale.ROOT));
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
                .credentialConfigured(trimToNull(connection.getApiKeyCiphertext()) != null)
                .apiFilingMediaTypes(List.copyOf(parseMediaTypes(connection.getApiFilingMediaTypes())))
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

    private String normalizeBaseUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private Set<MediaType> normalizedMediaTypes(List<MediaType> values) {
        if (values.isEmpty()) {
            return Set.of();
        }
        EnumSet<MediaType> result = EnumSet.noneOf(MediaType.class);
        result.addAll(values);
        return result;
    }

    private Set<MediaType> enumSet(Set<MediaType> values) {
        EnumSet<MediaType> result = EnumSet.noneOf(MediaType.class);
        result.addAll(values);
        return result;
    }

    private Set<MediaType> parseMediaTypes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            MediaType[] values = objectMapper.readValue(value, MediaType[].class);
            return values.length == 0 ? Set.of() : EnumSet.copyOf(Arrays.asList(values));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("API报白媒体配置无法解析", exception);
        }
    }

    private String writeMediaTypes(Set<MediaType> values) {
        try {
            return objectMapper.writeValueAsString(values.stream().sorted().toList());
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("API报白媒体配置无法保存", exception);
        }
    }
}
