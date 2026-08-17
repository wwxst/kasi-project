package com.kasi.backend.provider.mapper;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("短剧平台接入持久层")
class ProviderPersistenceTest extends BaseAuthTest {

    @Autowired
    private ShortDramaProviderMapper providerMapper;

    @Autowired
    private ShortDramaConnectionMapper connectionMapper;

    @Test
    @DisplayName("可以按编码和ID读取GoodShort平台")
    void providerCanBeReadByCodeAndId() {
        ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");

        assertThat(provider).isNotNull();
        assertThat(provider.getProviderName()).isEqualTo("GoodShort");
        assertThat(providerMapper.findById(provider.getId()).getProviderCode()).isEqualTo("GOODSHORT");
        assertThat(providerMapper.findAll()).extracting(ShortDramaProvider::getProviderCode)
                .containsExactly("GOODSHORT");
    }

    @Test
    @DisplayName("接入账号按平台唯一并可更新非密钥资料")
    void connectionIsUniquePerProviderAndUpdatable() {
        ShortDramaProvider provider = providerMapper.findByCode("GOODSHORT");
        ShortDramaConnection connection = connection(provider.getId(), "v1:ciphertext");

        assertThat(connection.toString()).doesNotContain("v1:ciphertext");
        assertThat(connectionMapper.insert(connection)).isEqualTo(1);
        connection.setConnectionName("GoodShort默认账号");
        connection.setCurrency("USD");
        connection.setApiKeyCiphertext(null);
        assertThat(connectionMapper.update(connection)).isEqualTo(1);

        ShortDramaConnection stored = connectionMapper.findByProviderId(provider.getId());
        assertThat(stored.getConnectionName()).isEqualTo("GoodShort默认账号");
        assertThat(stored.getApiKeyCiphertext()).isEqualTo("v1:ciphertext");
    }

    @Test
    @DisplayName("同一平台不能插入两套接入账号")
    void duplicateConnectionForProviderIsRejected() {
        Long providerId = providerMapper.findByCode("GOODSHORT").getId();
        connectionMapper.insert(connection(providerId, "v1:first"));

        assertThatThrownBy(() -> connectionMapper.insert(connection(providerId, "v1:second")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("接入账号Mapper不输出包含密文的DEBUG参数日志")
    void connectionMapperDebugLoggingIsDisabled() throws Exception {
        String logger = "logging.level.com.kasi.backend.provider.mapper.ShortDramaConnectionMapper=INFO";
        assertThat(Files.readString(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)).contains(logger);
        assertThat(Files.readString(
                Path.of("src/test/resources/application-test.properties"), StandardCharsets.UTF_8)).contains(logger);
    }

    private ShortDramaConnection connection(Long providerId, String ciphertext) {
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setProviderId(providerId);
        connection.setConnectionName("GoodShort接入账号");
        connection.setPartnerId("partner-1");
        connection.setApiKeyCiphertext(ciphertext);
        connection.setCurrency("USD");
        connection.setStatus(1);
        connection.setCreatedBy(1L);
        connection.setUpdatedBy(1L);
        return connection;
    }
}
