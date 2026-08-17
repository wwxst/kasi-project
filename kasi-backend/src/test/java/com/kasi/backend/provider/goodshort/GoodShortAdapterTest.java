package com.kasi.backend.provider.goodshort;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("GoodShort平台适配器")
class GoodShortAdapterTest {

    private static final long TIMESTAMP = 1681810530092L;
    private static final String API_KEY = "aaabbbccc";
    private static final ProviderConnectionSecret CONNECTION =
            new ProviderConnectionSecret("partner-1", API_KEY, "USD");

    private MockRestServiceServer server;
    private GoodShortAdapter adapter;
    private GoodShortSigner signer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://goodshort.test");
        server = MockRestServiceServer.bindTo(builder).build();
        signer = new GoodShortSigner();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(TIMESTAMP), ZoneOffset.UTC);
        adapter = new GoodShortAdapter(builder.build(), signer, clock);
    }

    @Test
    @DisplayName("连接探测发送固定最小请求和正确签名")
    void probeSendsSignedMinimumRequest() {
        Map<String, Object> parameters = parameters();
        server.expect(once(), requestTo("https://goodshort.test/open/book/initBooks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {
                          "pageNo": 1,
                          "pageSize": 1,
                          "language": "ENGLISH",
                          "pid": "partner-1",
                          "timestamp": 1681810530092
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(
                        "{\"status\":0,\"success\":true,\"message\":\"success\"}",
                        MediaType.APPLICATION_JSON));

        var result = adapter.testConnection(CONNECTION);

        assertThat(result.isReachable()).isTrue();
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getTestedAt()).isEqualTo(Instant.ofEpochMilli(TIMESTAMP));
        server.verify();
    }

    @Test
    @DisplayName("GoodShort业务状态非零时返回平台拒绝错误")
    void nonzeroStatusIsRejectedWithoutLeakingCredential() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess(
                        "{\"status\":1001,\"success\":false,\"message\":\"invalid\"}",
                        MediaType.APPLICATION_JSON));

        assertRemoteFailure(6006);
    }

    @Test
    @DisplayName("GoodShort服务端错误转换为平台暂不可用")
    void serverFailureIsUnavailableWithoutLeakingCredential() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withServerError());

        assertRemoteFailure(6005);
    }

    @Test
    @DisplayName("GoodShort网络异常转换为平台暂不可用")
    void ioFailureIsUnavailableWithoutLeakingCredential() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withException(new IOException("network down " + API_KEY)));

        assertRemoteFailure(6005);
    }

    @Test
    @DisplayName("GoodShort声明能力不包含TikTok锚点")
    void capabilitiesExcludeTikTokAnchor() {
        assertThat(adapter.providerCode()).isEqualTo("GOODSHORT");
        assertThat(adapter.capabilities())
                .contains(ProviderCapability.ACCOUNT_FILING, ProviderCapability.ORDER_SYNC)
                .doesNotContain(ProviderCapability.TIKTOK_ANCHOR);
    }

    private void assertRemoteFailure(int code) {
        assertThatThrownBy(() -> adapter.testConnection(CONNECTION))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).doesNotContain(API_KEY).doesNotContain("partner-1");
                });
        server.verify();
    }

    private Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pageNo", 1);
        parameters.put("pageSize", 1);
        parameters.put("language", "ENGLISH");
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        return parameters;
    }
}
