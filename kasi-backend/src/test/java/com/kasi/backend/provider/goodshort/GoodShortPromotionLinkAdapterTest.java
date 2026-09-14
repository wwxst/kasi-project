package com.kasi.backend.provider.goodshort;

import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoodShortPromotionLinkAdapterTest {
    private MockRestServiceServer server;
    private GoodShortAdapter adapter;
    private GoodShortSigner signer;
    private static final String KEY = "aaabbbccc";
    private static final ProviderConnectionSecret CONNECTION =
            new ProviderConnectionSecret("https://goodshort.test", "partner-1", KEY, "USD");

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://goodshort.test");
        server = MockRestServiceServer.bindTo(builder).build();
        signer = new GoodShortSigner();
        adapter = new GoodShortAdapter(builder.build(), signer,
                Clock.fixed(Instant.ofEpochMilli(1681810530092L), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("生成口令使用短剧追踪号和媒体映射")
    void generatesPromotionLink() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("pid", "partner-1");
        parameters.put("bookId", "book-1");
        parameters.put("customParams", "583729104628");
        parameters.put("shareUrlType", 1);
        parameters.put("codeMedia", "TIKTOK");
        parameters.put("timestamp", 1681810530092L);
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andExpect(header("sign", signer.sign(parameters, KEY)))
                .andExpect(content().json("""
                        {"pid":"partner-1","bookId":"book-1","customParams":"583729104628",
                         "shareUrlType":1,"codeMedia":"TIKTOK","timestamp":1681810530092}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"code":"54788","customParams":"583729104628",
                         "shareUrl":"https://demo.com/koc/GRKOC00001/54788-KOC"}}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING"));

        assertThat(result.externalCode()).isEqualTo("54788");
        assertThat(result.shareUrl()).contains("54788");
        server.verify();
    }

    @Test
    @DisplayName("OneLink变体发送shareUrlType=2")
    void generatesOneLinkVariant() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("pid", "partner-1"); parameters.put("bookId", "book-1");
        parameters.put("customParams", "583729104628"); parameters.put("shareUrlType", 2);
        parameters.put("codeMedia", "YOUTUBE"); parameters.put("timestamp", 1681810530092L);
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andExpect(header("sign", signer.sign(parameters, KEY)))
                .andExpect(content().json("{\"pid\":\"partner-1\",\"bookId\":\"book-1\",\"customParams\":\"583729104628\",\"shareUrlType\":2,\"codeMedia\":\"YOUTUBE\",\"timestamp\":1681810530092}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,\"data\":{\"code\":\"54786\",\"customParams\":\"583729104628\",\"shareUrl\":\"https://demo.com/one\"}}", MediaType.APPLICATION_JSON));

        var result = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.YOUTUBE, "ONELINK"));
        assertThat(result.externalCode()).isEqualTo("54786");
        server.verify();
    }

    @Test
    @DisplayName("甲方返回20005但带有效数据时仍返回原口令和当前链接")
    void acceptsAlreadyGeneratedResponseWithData() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("pid", "partner-1"); parameters.put("bookId", "book-1");
        parameters.put("customParams", "583729104628"); parameters.put("shareUrlType", 2);
        parameters.put("codeMedia", "TIKTOK"); parameters.put("timestamp", 1681810530092L);
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andExpect(content().json("{\"pid\":\"partner-1\",\"bookId\":\"book-1\",\"customParams\":\"583729104628\",\"shareUrlType\":2,\"codeMedia\":\"TIKTOK\",\"timestamp\":1681810530092}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"status\":20005,\"success\":false,\"data\":{\"code\":\"54788\",\"shareUrl\":\"https://demo.com/one\"}}", MediaType.APPLICATION_JSON));

        var result = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "ONELINK"));

        assertThat(result.externalCode()).isEqualTo("54788");
        assertThat(result.shareUrl()).isEqualTo("https://demo.com/one");
        server.verify();
    }

    @Test
    @DisplayName("LANDING和OneLink仅发送不同shareUrlType")
    void variantsKeepTheSamePromotionIdentityParameters() {
        GoodShortPromotionLinkRateLimiter limiter = new GoodShortPromotionLinkRateLimiter(
                Duration.ofNanos(1), System::nanoTime, ignored -> { });
        RestClient.Builder builder = RestClient.builder().baseUrl("https://goodshort.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoodShortAdapter(builder.build(), signer,
                Clock.fixed(Instant.ofEpochMilli(1681810530092L), ZoneOffset.UTC), limiter);
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andExpect(content().json("{\"pid\":\"partner-1\",\"bookId\":\"book-1\",\"customParams\":\"583729104628\",\"shareUrlType\":1,\"codeMedia\":\"TIKTOK\",\"timestamp\":1681810530092}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,\"data\":{\"code\":\"54788\",\"shareUrl\":\"https://demo.com/landing\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andExpect(content().json("{\"pid\":\"partner-1\",\"bookId\":\"book-1\",\"customParams\":\"583729104628\",\"shareUrlType\":2,\"codeMedia\":\"TIKTOK\",\"timestamp\":1681810530092}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"status\":20005,\"success\":false,\"data\":{\"code\":\"54788\",\"shareUrl\":\"https://demo.com/one\"}}", MediaType.APPLICATION_JSON));

        var landing = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING"));
        var oneLink = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "ONELINK"));

        assertThat(landing.externalCode()).isEqualTo(oneLink.externalCode());
        assertThat(landing.shareUrl()).isNotEqualTo(oneLink.shareUrl());
        server.verify();
    }

    @Test
    @DisplayName("20005缺少完整数据时仍按甲方拒绝处理")
    void rejectsAlreadyGeneratedResponseWithoutCompleteData() {
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andRespond(withSuccess("{\"status\":20005,\"success\":false,\"data\":{\"code\":\"54788\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING")))
                .isInstanceOf(com.kasi.backend.provider.exception.ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("20005返回空白口令和链接时仍按甲方拒绝处理")
    void rejectsAlreadyGeneratedResponseWithBlankData() {
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andRespond(withSuccess("{\"status\":20005,\"success\":false,\"data\":{\"code\":\" \",\"shareUrl\":\" \"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING")))
                .isInstanceOf(com.kasi.backend.provider.exception.ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("20005以外的非零状态即使带数据也继续失败")
    void rejectsOtherNonZeroStatusWithData() {
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andRespond(withSuccess("{\"status\":20006,\"success\":false,\"data\":{\"code\":\"54788\",\"shareUrl\":\"https://demo.com/landing\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING")))
                .isInstanceOf(com.kasi.backend.provider.exception.ProviderRemoteRejectedException.class);
        server.verify();
    }

    @Test
    @DisplayName("媒体类型为空时直接暴露程序错误而不是发送UNKNOWN")
    void nullMediaTypeDoesNotFallBackToUnknown() {
        assertThatThrownBy(() -> adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "583729104628", null, "LANDING")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("连续生成同一甲方唯一组合时使用两秒专用限流")
    void generationUsesPromotionLinkRateLimiter() {
        AtomicLong now = new AtomicLong(0);
        AtomicLong slept = new AtomicLong();
        GoodShortPromotionLinkRateLimiter limiter = new GoodShortPromotionLinkRateLimiter(
                Duration.ofSeconds(2), now::get, nanos -> {
                    slept.addAndGet(nanos);
                    now.addAndGet(nanos);
                });
        RestClient.Builder builder = RestClient.builder().baseUrl("https://goodshort.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoodShortAdapter(builder.build(), signer,
                Clock.fixed(Instant.ofEpochMilli(1681810530092L), ZoneOffset.UTC), limiter);
        String response = """
                {"status":0,"success":true,"data":{"code":"54788","customParams":"583729104628",
                 "shareUrl":"https://demo.com/koc/54788"}}""";
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://goodshort.test/creek/open/inviteCode/generate/partner/code"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        PromotionLinkRequest request = new PromotionLinkRequest("book-1", "583729104628",
                com.kasi.backend.promotion.enums.MediaType.TIKTOK, "LANDING");

        adapter.generatePromotionLink(CONNECTION, request);
        adapter.generatePromotionLink(CONNECTION, request);

        assertThat(slept).hasValue(Duration.ofSeconds(2).toNanos());
        server.verify();
    }
}
