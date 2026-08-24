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
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
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
        parameters.put("customParams", "tracking-1");
        parameters.put("shareUrlType", 1);
        parameters.put("codeMedia", "TIKTOK");
        parameters.put("timestamp", 1681810530092L);
        server.expect(requestTo("https://goodshort.test/open/inviteCode/generate/partner/code"))
                .andExpect(header("sign", signer.sign(parameters, KEY)))
                .andExpect(content().json("""
                        {"pid":"partner-1","bookId":"book-1","customParams":"tracking-1",
                         "shareUrlType":1,"codeMedia":"TIKTOK","timestamp":1681810530092}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"code":"54788","customParams":"tracking-1",
                         "shareUrl":"https://demo.com/koc/GRKOC00001/54788-KOC"}}
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.generatePromotionLink(CONNECTION,
                new PromotionLinkRequest("book-1", "tracking-1",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "DEFAULT"));

        assertThat(result.externalCode()).isEqualTo("54788");
        assertThat(result.customParams()).isEqualTo("tracking-1");
        assertThat(result.shareUrl()).contains("54788");
        server.verify();
    }
}
