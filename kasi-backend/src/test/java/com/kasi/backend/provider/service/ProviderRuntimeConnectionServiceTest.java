package com.kasi.backend.provider.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.enums.FilingMode;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.ProviderAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("平台运行时连接")
class ProviderRuntimeConnectionServiceTest {

    private ShortDramaProviderMapper providerMapper;
    private ShortDramaConnectionMapper connectionMapper;
    private ProviderCredentialCipher credentialCipher;
    private AccountFilingProviderAdapter adapter;
    private ProviderRuntimeConnectionService service;

    @BeforeEach
    void setUp() {
        providerMapper = mock(ShortDramaProviderMapper.class);
        connectionMapper = mock(ShortDramaConnectionMapper.class);
        credentialCipher = mock(ProviderCredentialCipher.class);
        adapter = mock(AccountFilingProviderAdapter.class);
        when(adapter.providerCode()).thenReturn("GOODSHORT");
        when(adapter.capabilities()).thenReturn(java.util.Set.of(ProviderCapability.ACCOUNT_FILING));
        service = new com.kasi.backend.provider.service.impl.ProviderRuntimeConnectionServiceImpl(
                providerMapper, connectionMapper, credentialCipher, new ProviderAdapterRegistry(java.util.List.of(adapter)));
    }

    @Test
    @DisplayName("运行时连接只向适配器提供解密后的短生命周期密钥")
    void resolveReturnsRuntimeSecretWithoutCiphertext() {
        when(providerMapper.findById(1L)).thenReturn(provider(1));
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection(1));
        when(credentialCipher.decrypt("v1:cipher")).thenReturn("remote-key");

        var result = service.resolve(1L, ProviderCapability.ACCOUNT_FILING);

        assertThat(result.connectionId()).isEqualTo(10L);
        assertThat(result.secret().getApiKey()).isEqualTo("remote-key");
        assertThat(result.toString()).doesNotContain("remote-key", "v1:cipher");
    }

    @Test
    @DisplayName("平台停用时不返回运行时连接")
    void disabledProviderIsRejected() {
        when(providerMapper.findById(1L)).thenReturn(provider(0));

        assertThatThrownBy(() -> service.resolve(1L, ProviderCapability.ACCOUNT_FILING))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6003));
    }

    @Test
    @DisplayName("人工报备连接无 API 凭据时仍可用于建立报备记录")
    void resolveAllIncludesManualConnectionWithoutCredentials() {
        ShortDramaProvider provider = provider(1);
        ShortDramaConnection connection = connection(1);
        connection.setBaseUrl(null);
        connection.setPartnerId(null);
        connection.setApiKeyCiphertext(null);
        connection.setFilingMode(FilingMode.MANUAL);
        when(providerMapper.findAll()).thenReturn(java.util.List.of(provider));
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection);

        var result = service.resolveAll(ProviderCapability.ACCOUNT_FILING);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().connectionId()).isEqualTo(10L);
        assertThat(result.getFirst().secret().getApiKey()).isNull();
    }

    private ShortDramaProvider provider(int status) {
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(1L);
        provider.setProviderCode("GOODSHORT");
        provider.setProviderName("GoodShort");
        provider.setStatus(status);
        return provider;
    }

    private ShortDramaConnection connection(int status) {
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(10L);
        connection.setProviderId(1L);
        connection.setBaseUrl("https://goodshort.test");
        connection.setPartnerId("partner-1");
        connection.setApiKeyCiphertext("v1:cipher");
        connection.setCurrency("USD");
        connection.setStatus(status);
        return connection;
    }
}
