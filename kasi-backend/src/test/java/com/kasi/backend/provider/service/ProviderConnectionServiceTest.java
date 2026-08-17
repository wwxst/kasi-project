package com.kasi.backend.provider.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.service.impl.ProviderConnectionServiceImpl;
import com.kasi.backend.provider.vo.ProviderVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
    @InjectMocks private ProviderConnectionServiceImpl service;

    @Test
    @DisplayName("查询接入账号只返回是否配置密钥")
    void listDoesNotExposeCredential() {
        when(providerMapper.findAll()).thenReturn(List.of(provider()));
        when(connectionMapper.findByProviderId(1L)).thenReturn(connection("v1:secret-ciphertext"));

        ProviderVO result = service.getProviders().getFirst();

        assertThat(result.getConnection().isCredentialConfigured()).isTrue();
        assertThat(result.getConnection().toString()).doesNotContain("secret-ciphertext");
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

        var result = service.upsert(9L, 1L, request("secret-key", 0));

        ArgumentCaptor<ShortDramaConnection> captor = ArgumentCaptor.forClass(ShortDramaConnection.class);
        verify(connectionMapper).insert(captor.capture());
        ShortDramaConnection inserted = captor.getValue();
        assertThat(inserted.getConnectionName()).isEqualTo("GoodShort账号");
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
        request.setConnectionName("  GoodShort账号  ");
        request.setPartnerId("  partner-1  ");
        request.setApiKey(apiKey);
        request.setCurrency(" usd ");
        request.setStatus(status);
        return request;
    }
}
