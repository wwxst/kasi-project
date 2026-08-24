package com.kasi.backend.provider.goodshort;

import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.spi.DramaCatalogFetchRequest;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("GoodShort鐭墽鐩綍閫傞厤鍣?")
class GoodShortCatalogAdapterTest {

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
        adapter = new GoodShortAdapter(builder.build(), signer,
                Clock.fixed(Instant.ofEpochMilli(TIMESTAMP), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("鍏ㄩ噺鍚屾鍙戦€乮nitBooks璇锋眰骞舵槧灏勭煭鍓у拰鍓ч泦")
    void fetchFullMapsBookAndEpisodes() {
        var parameters = parameters(1, 100, "ENGLISH");
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {"pageNo":1,"pageSize":100,"language":"ENGLISH","pid":"partner-1","timestamp":1681810530092}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"pageNo":1,"pageSize":100,"total":1,"hasNext":false,
                         "items":[{"bookId":"book-1","bookName":"Title","originalBookName":"Original",
                         "introduction":"Intro","cover":"https://img/1","language":"ENGLISH","type":"SERIES",
                         "showStatus":1,"updateTime":"2025-08-28T11:26:18.000+0000",
                         "episodes":[{"episodeId":"ep-1","episodeNo":1,"title":"Episode 1","isFree":true,
                         "duration":42,"updateTime":"2025-08-28T12:26:18.000+0000"}]}]}}
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.fetchFullDramas(CONNECTION, new DramaCatalogFetchRequest(1, 100, "ENGLISH"));

        assertThat(page.items()).hasSize(1);
        var book = page.items().getFirst();
        assertThat(book.externalDramaId()).isEqualTo("book-1");
        assertThat(book.title()).isEqualTo("Title");
        assertThat(book.remoteShowStatus()).isEqualTo("1");
        assertThat(book.contents()).singleElement().satisfies(content -> {
            assertThat(content.externalContentId()).isEqualTo("ep-1");
            assertThat(content.sequenceNo()).isEqualTo(1);
            assertThat(content.free()).isTrue();
            assertThat(content.durationSeconds()).isEqualTo(42);
        });
        server.verify();
    }

    @Test
    @DisplayName("澧為噺鍚屾鍖呭惈updateTime骞惰繑鍥炴柊妫€鏌ョ偣")
    void fetchIncrementalSendsWatermark() {
        var parameters = parameters(2, 50, "ENGLISH");
        parameters.put("updateTime", 1700000000000L);
        server.expect(requestTo("https://goodshort.test/open/book/incrementBooks"))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {"pageNo":2,"pageSize":50,"language":"ENGLISH","pid":"partner-1","timestamp":1681810530092,"updateTime":1700000000000}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"pageNo":2,"pageSize":50,"total":51,"hasNext":false,"nextUpdateTime":1700000001234,"items":[]}}
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.fetchIncrementalDramas(CONNECTION,
                new DramaCatalogFetchRequest(2, 50, "ENGLISH", 1700000000000L));
        assertThat(page.nextUpdateTime()).isEqualTo(1700000001234L);
        assertThat(page.hasNext()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("鏃堕棿鏄剧ず鏀寔 ISO Z 鍜?+0000 鍋忕Щ")
    void fetchMapsZuluAndCompactOffsetTimes() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"pageNo":1,"pageSize":10,"total":1,"hasNext":false,
                         "items":[{"bookId":"book-time","bookName":"Time","updateTime":"2025-08-28T11:26:18Z",
                         "episodes":[{"episodeId":"ep-time","episodeNo":1,"isFree":true,
                         "updateTime":"2025-08-28T12:26:18+0000"}]}]}}
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.fetchFullDramas(CONNECTION, new DramaCatalogFetchRequest(1, 10, "ENGLISH"));

        assertThat(page.items().getFirst().remoteUpdatedAt()).isNotNull();
        assertThat(page.items().getFirst().contents().getFirst().remoteUpdatedAt()).isNotNull();
        server.verify();
    }

    @Test
    @DisplayName("绌哄墽闆嗗厓绱犳寜骞冲彴鎷掔粷澶勭悊锛屼笉鎶涘嚭 NPE")
    void nullEpisodeElementIsRejected() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"pageNo":1,"pageSize":10,"total":1,"hasNext":false,
                         "items":[{"bookId":"book-null-episode","bookName":"Invalid","episodes":[null]}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH")))
                .isInstanceOf(ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("涓氬姟澶辫触銆佺┖data鍜屾湭鐭ユ牸寮忕粺涓€鎶ュ钩鍙版嫆缁濓紒")
    void businessAndMalformedResponsesAreRejected() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("{\"status\":1001,\"success\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,\"data\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,\"data\":{\"items\":null}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderRemoteRejectedException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderRemoteRejectedException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderRemoteRejectedException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderRemoteRejectedException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("缃戠粶銆?xx鍜?29杩斿洖鏆傛椂寮傚父")
    void transientFailuresAreRetryable() {
        server.expect(requestTo("https://goodshort.test/open/book/initBooks")).andRespond(withServerError());
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(withException(new IOException("network down")));
        server.expect(requestTo("https://goodshort.test/open/book/initBooks"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderTransientException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderTransientException.class);
        assertThatThrownBy(() -> adapter.fetchFullDramas(CONNECTION,
                new DramaCatalogFetchRequest(1, 10, "ENGLISH"))).isInstanceOf(ProviderTransientException.class);
        server.verify();
    }

    private Map<String, Object> parameters(int pageNo, int pageSize, String language) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pageNo", pageNo);
        parameters.put("pageSize", pageSize);
        parameters.put("language", language);
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        return parameters;
    }
}
