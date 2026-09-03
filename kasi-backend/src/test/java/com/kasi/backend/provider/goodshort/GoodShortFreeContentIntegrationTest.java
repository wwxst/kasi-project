package com.kasi.backend.provider.goodshort;

import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("real-smoke")
@EnabledIfSystemProperty(named = "goodshort.integration", matches = "true")
class GoodShortFreeContentIntegrationTest {

    @Test
    @DisplayName("真实GoodShort配置可以获取免费剧集资源")
    void fetchesFreeContentWithRealGoodShortConfiguration() {
        String baseUrl = requireEnvironment("GOODSHORT_BASE_URL");
        String partnerId = requireEnvironment("GOODSHORT_PARTNER_ID");
        String apiKey = requireEnvironment("GOODSHORT_API_KEY");
        String externalDramaId = requireEnvironment("DRAMA_EXTERNAL_ID");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        GoodShortAdapter adapter = new GoodShortAdapter(
                RestClient.builder().requestFactory(requestFactory).build(),
                new GoodShortSigner(), Clock.systemUTC());

        var result = adapter.fetchFreeContent(
                new ProviderConnectionSecret(baseUrl, partnerId, apiKey, "USD"), externalDramaId);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(item -> {
            assertThat(item.contentUrl()).isNotBlank();
            assertThat(URI.create(item.contentUrl()).getScheme()).isIn("http", "https");
        });
    }

    private String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("Missing required environment variable: " + name);
        }
        return value;
    }
}
