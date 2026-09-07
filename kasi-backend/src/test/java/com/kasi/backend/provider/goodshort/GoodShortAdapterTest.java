package com.kasi.backend.provider.goodshort;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.FreeContentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("GoodShort平台适配器")
class GoodShortAdapterTest {

    private static final long TIMESTAMP = 1681810530092L;
    private static final String API_KEY = "aaabbbccc";
    private static final ProviderConnectionSecret CONNECTION =
            new ProviderConnectionSecret("https://goodshort.test", "partner-1", API_KEY, "USD");

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
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/initBooks"))
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
        server.expect(requestTo("https://goodshort.test/creek/open/book/initBooks"))
                .andRespond(withSuccess(
                        "{\"status\":1001,\"success\":false,\"message\":\"invalid\"}",
                        MediaType.APPLICATION_JSON));

        assertRemoteFailure(6006);
    }

    @Test
    @DisplayName("GoodShort服务端错误转换为平台暂不可用")
    void serverFailureIsUnavailableWithoutLeakingCredential() {
        server.expect(requestTo("https://goodshort.test/creek/open/book/initBooks"))
                .andRespond(withServerError());

        assertRemoteFailure(6005);
    }

    @Test
    @DisplayName("GoodShort网络异常转换为平台暂不可用")
    void ioFailureIsUnavailableWithoutLeakingCredential() {
        server.expect(requestTo("https://goodshort.test/creek/open/book/initBooks"))
                .andRespond(withException(new IOException("network down " + API_KEY)));

        assertRemoteFailure(6005);
    }

    @Test
    @DisplayName("GoodShort声明能力不包含TikTok锚点")
    void capabilitiesExcludeTikTokAnchor() {
        assertThat(adapter.providerCode()).isEqualTo("GOODSHORT");
        assertThat(adapter.capabilities())
                .contains(ProviderCapability.ACCOUNT_FILING, ProviderCapability.ORDER_SYNC,
                        ProviderCapability.FREE_CONTENT_PREVIEW)
                .doesNotContain(ProviderCapability.TIKTOK_ANCHOR);
    }

    @Test
    @DisplayName("免费内容请求发送正确参数并返回剧集视频地址")
    void fetchFreeContentSendsSignedRequestAndMapsVideos() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        parameters.put("bookId", "book-1");
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {
                          "pid": "partner-1",
                          "timestamp": 1681810530092,
                          "bookId": "book-1"
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {
                          "status": 0,
                          "success": true,
                          "data": [
                            {"chapterName": "Chapter 1", "content": "https://cdn.test/1.m3u8"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = ((com.kasi.backend.provider.spi.FreeContentProviderAdapter) adapter)
                .fetchFreeContent(CONNECTION, "book-1");

        assertThat(result).containsExactly(new FreeContentResult("Chapter 1", "https://cdn.test/1.m3u8"));
        server.verify();
    }

    @Test
    @DisplayName("连续免费内容请求经过专属限流后才发送")
    void freeContentRequestsUseDedicatedRateLimiter() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://goodshort.test");
        server = MockRestServiceServer.bindTo(builder).build();
        AtomicLong now = new AtomicLong(0);
        AtomicLong slept = new AtomicLong();
        GoodShortFreeContentRateLimiter limiter = new GoodShortFreeContentRateLimiter(
                Duration.ofMillis(650), now::get, nanos -> {
                    slept.addAndGet(nanos);
                    now.addAndGet(nanos);
                });
        adapter = new GoodShortAdapter(builder.build(), signer,
                Clock.fixed(Instant.ofEpochMilli(TIMESTAMP), ZoneOffset.UTC), limiter);
        String response = """
                {"status":0,"success":true,"data":[
                  {"chapterName":"Chapter 1","content":"https://cdn.test/1.m3u8"}
                ]}
                """;
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        adapter.fetchFreeContent(CONNECTION, "book-1");
        adapter.fetchFreeContent(CONNECTION, "book-1");

        assertThat(slept).hasValue(Duration.ofMillis(650).toNanos());
        server.verify();
    }

    @Test
    @DisplayName("免费内容请求收到429时仍按临时错误处理")
    void freeContentRateLimitResponseRemainsTransient() {
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> adapter.fetchFreeContent(CONNECTION, "book-1"))
                .isInstanceOf(ProviderTransientException.class);
        server.verify();
    }

    @Test
    @DisplayName("免费内容包含空白地址时拒绝整次响应")
    void freeContentRejectsBlankContent() {
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":[
                          {"chapterName":"Chapter 1","content":"https://cdn.test/1.m3u8"},
                          {"chapterName":"Chapter 2","content":"  "}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetchFreeContent(CONNECTION, "book-1"))
                .isInstanceOf(ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("免费内容包含null记录时拒绝整次响应")
    void freeContentRejectsNullItem() {
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":[
                          {"chapterName":"Chapter 1","content":"https://cdn.test/1.m3u8"},
                          null
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetchFreeContent(CONNECTION, "book-1"))
                .isInstanceOf(ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("免费内容空数组保持有效")
    void freeContentAllowsEmptyData() {
        server.expect(once(), requestTo("https://goodshort.test/creek/open/book/freeContent"))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.fetchFreeContent(CONNECTION, "book-1")).isEmpty();
        server.verify();
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
