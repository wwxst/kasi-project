package com.kasi.backend.provider.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.service.impl.ProviderConnectionServiceImpl;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("短剧平台接入账号服务")
class ProviderConnectionServiceTest {

    @Mock private ShortDramaProviderMapper providerMapper;
    @Mock private ShortDramaConnectionMapper connectionMapper;
    @Mock private ProviderCredentialCipher credentialCipher;
    @Mock private ProviderAdapter adapter;
    private ProviderConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProviderConnectionServiceImpl(
                providerMapper, connectionMapper, credentialCipher, List.of(adapter));
    }

    @Test
    @DisplayName("查询接入账号只返回是否配置密钥")
    void listDoesNotExposeCredential() {
        when(providerMapper.findAll()).thenReturn(List.of(provider()));
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection("v1:secret-ciphertext"));
        when(adapter.providerCode()).thenReturn("GOODSHORT");
        when(adapter.capabilities()).thenReturn(java.util.Set.of(
                ProviderCapability.ACCOUNT_FILING, ProviderCapability.ORDER_SYNC));

        ProviderVO result = service.getProviders().getFirst();

        assertThat(result.getConnection().isCredentialConfigured()).isTrue();
        assertThat(result.getConnection().toString()).doesNotContain("secret-ciphertext");
        assertThat(result.getCapabilities())
                .containsExactlyInAnyOrder(ProviderCapability.ACCOUNT_FILING, ProviderCapability.ORDER_SYNC);
    }

    @Test
    @DisplayName("首次配置接入账号必须提供平台密钥")
    void createRequiresApiKey() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.upsert(9L, 1L, request("  ", 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6003));
        verify(connectionMapper, never()).insert(any());
    }

    @Test
    @DisplayName("首次配置会规范化资料并只向Mapper传递密文")
    void createNormalizesAndEncryptsCredential() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(null, connection("v1:encrypted"));
        when(credentialCipher.encrypt("secret-key")).thenReturn("v1:encrypted");
        when(connectionMapper.insert(any())).thenReturn(1);

        UpsertProviderConnectionDTO request = request("secret-key", 0);
        assertThat(request.toString()).doesNotContain("secret-key");

        var result = service.upsert(9L, 1L, request);

        ArgumentCaptor<ShortDramaConnection> captor = ArgumentCaptor.forClass(ShortDramaConnection.class);
        verify(connectionMapper).insert(captor.capture());
        ShortDramaConnection inserted = captor.getValue();
        assertThat(inserted.getConnectionName()).isEqualTo("GoodShort账号");
        assertThat(inserted.getBaseUrl()).isEqualTo("https://api.goodshort.test/creek");
        assertThat(inserted.getPartnerId()).isEqualTo("partner-1");
        assertThat(inserted.getCurrency()).isEqualTo("USD");
        assertThat(inserted.getStatus()).isZero();
        assertThat(inserted.getCreatedBy()).isEqualTo(9L);
        assertThat(inserted.getUpdatedBy()).isEqualTo(9L);
        assertThat(inserted.getApiKeyCiphertext()).isEqualTo("v1:encrypted");
        assertThat(inserted.toString()).doesNotContain("secret-key");
        assertThat(result.isCredentialConfigured()).isTrue();
    }

    @Test
    @DisplayName("更新未提供密钥时不覆盖原密文")
    void updateRetainsOmittedCredential() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L))
                .thenReturn(connection("v1:old-ciphertext"), connection("v1:old-ciphertext"));
        when(connectionMapper.update(any())).thenReturn(1);

        service.upsert(9L, 1L, request("  ", 1));

        ArgumentCaptor<ShortDramaConnection> captor = ArgumentCaptor.forClass(ShortDramaConnection.class);
        verify(connectionMapper).update(captor.capture());
        assertThat(captor.getValue().getApiKeyCiphertext()).isNull();
        verify(credentialCipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("更新提供新密钥时使用新密文替换")
    void updateReplacesSuppliedCredential() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L))
                .thenReturn(connection("v1:old-ciphertext"), connection("v1:new-ciphertext"));
        when(credentialCipher.encrypt("new-secret")).thenReturn("v1:new-ciphertext");
        when(connectionMapper.update(any())).thenReturn(1);

        service.upsert(9L, 1L, request("new-secret", 1));

        ArgumentCaptor<ShortDramaConnection> captor = ArgumentCaptor.forClass(ShortDramaConnection.class);
        verify(connectionMapper).update(captor.capture());
        assertThat(captor.getValue().getApiKeyCiphertext()).isEqualTo("v1:new-ciphertext");
    }

    @Test
    @DisplayName("不存在的平台返回稳定业务错误")
    void missingProviderIsRejected() {
        when(providerMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.upsert(9L, 99L, request("secret", 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6001));
        verify(connectionMapper, never()).insert(any());
        verify(connectionMapper, never()).update(any());
    }

    @Test
    @DisplayName("接入账号状态只允许启用或禁用")
    void statusOnlyAllowsEnabledOrDisabledValues() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        UpsertProviderConnectionDTO request = request("secret", 0);
        request.setCurrency("USD");

        assertThat(validator.validate(request)).isEmpty();
        request.setStatus(2);
        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("status");
    }

    @Test
    @DisplayName("连接测试解密密钥后按平台编码调用适配器")
    void testConnectionDecryptsAndDelegatesToMatchingAdapter() {
        ShortDramaConnection connection = connection("v1:ciphertext");
        when(adapter.providerCode()).thenReturn("GOODSHORT");
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection);
        when(credentialCipher.decrypt("v1:ciphertext")).thenReturn("plain-secret");
        ProviderConnectionTestVO expected = ProviderConnectionTestVO.builder()
                .reachable(true)
                .message("success")
                .build();
        when(adapter.testConnection(any())).thenReturn(expected);

        assertThat(service.testConnection(1L)).isSameAs(expected);

        ArgumentCaptor<ProviderConnectionSecret> secretCaptor =
                ArgumentCaptor.forClass(ProviderConnectionSecret.class);
        verify(adapter).testConnection(secretCaptor.capture());
        assertThat(secretCaptor.getValue().getBaseUrl()).isEqualTo("https://api.goodshort.test/creek");
        assertThat(secretCaptor.getValue().getPartnerId()).isEqualTo("partner-1");
        assertThat(secretCaptor.getValue().getApiKey()).isEqualTo("plain-secret");
        assertThat(secretCaptor.getValue().getCurrency()).isEqualTo("USD");
        assertThat(secretCaptor.getValue().toString()).doesNotContain("plain-secret");
    }

    @Test
    @DisplayName("连接未配置时不调用平台适配器")
    void testConnectionRejectsMissingConnectionBeforeNetwork() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.testConnection(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6002));
        verify(adapter, never()).testConnection(any());
    }

    @Test
    @DisplayName("平台或接入账号禁用时不调用平台适配器")
    void testConnectionRejectsDisabledConfigurationBeforeNetwork() {
        ShortDramaProvider provider = provider();
        provider.setStatus(0);
        when(providerMapper.findById(1L)).thenReturn(provider);

        assertThatThrownBy(() -> service.testConnection(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6003));
        verify(adapter, never()).testConnection(any());
    }

    @Test
    @DisplayName("接入账号禁用时不调用平台适配器")
    void testConnectionRejectsDisabledConnectionBeforeNetwork() {
        ShortDramaConnection connection = connection("v1:ciphertext");
        connection.setStatus(0);
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection);

        assertThatThrownBy(() -> service.testConnection(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6003));
        verify(credentialCipher, never()).decrypt(any());
        verify(adapter, never()).testConnection(any());
    }

    @Test
    @DisplayName("接入账号资料不完整时不解密也不调用平台")
    void testConnectionRejectsIncompleteConnectionBeforeNetwork() {
        ShortDramaConnection connection = connection("v1:ciphertext");
        connection.setPartnerId("  ");
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection);

        assertThatThrownBy(() -> service.testConnection(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6003));
        verify(credentialCipher, never()).decrypt(any());
        verify(adapter, never()).testConnection(any());
    }

    @Test
    @DisplayName("密钥无法解密时返回凭据不可用且不调用平台")
    void testConnectionMapsDecryptionFailureWithoutNetwork() {
        when(providerMapper.findById(1L)).thenReturn(provider());
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection("v1:bad"));
        when(credentialCipher.decrypt("v1:bad")).thenThrow(new IllegalStateException("平台密钥无法解密"));

        assertThatThrownBy(() -> service.testConnection(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6004));
        verify(adapter, never()).testConnection(any());
    }

    private ShortDramaProvider provider() {
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(1L);
        provider.setProviderCode("GOODSHORT");
        provider.setProviderName("GoodShort");
        provider.setStatus(1);
        return provider;
    }

    private ShortDramaConnection connection(String ciphertext) {
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(2L);
        connection.setProviderId(1L);
        connection.setConnectionName("GoodShort账号");
        connection.setBaseUrl("https://api.goodshort.test/creek");
        connection.setPartnerId("partner-1");
        connection.setApiKeyCiphertext(ciphertext);
        connection.setCurrency("USD");
        connection.setStatus(1);
        connection.setCreatedAt(LocalDateTime.of(2026, 8, 17, 10, 0));
        connection.setUpdatedAt(LocalDateTime.of(2026, 8, 17, 11, 0));
        return connection;
    }

    private UpsertProviderConnectionDTO request(String apiKey, Integer status) {
        UpsertProviderConnectionDTO request = new UpsertProviderConnectionDTO();
        request.setBaseUrl("https://api.goodshort.test/creek");
        request.setConnectionName("  GoodShort账号  ");
        request.setPartnerId("  partner-1  ");
        request.setApiKey(apiKey);
        request.setCurrency(" usd ");
        request.setStatus(status);
        return request;
    }
}
