package com.kasi.backend.provider.goodshort;

import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.spi.AccountFilingQuery;
import com.kasi.backend.provider.spi.AccountFilingSubmission;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("GoodShort账号报备适配器")
class GoodShortFilingAdapterTest {

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
    @DisplayName("提交报备发送正确路径、字段和签名")
    void submitAccountFilingSendsSignedRequest() {
        var parameters = parameters();
        parameters.put("type", "ACCOUNT");
        parameters.put("media", "TIKTOK");
        parameters.put("accountId", "creator-1");
        parameters.put("accountName", "Creator One");
        parameters.put("accountLink", "https://www.tiktok.com/@creator-1");
        server.expect(requestTo("https://goodshort.test/open/filing/report"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {"pid":"partner-1","timestamp":1681810530092,"type":"ACCOUNT",
                         "media":"TIKTOK","accountId":"creator-1","accountName":"Creator One",
                         "accountLink":"https://www.tiktok.com/@creator-1"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"status\":0,\"success\":true,\"message\":\"success\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        adapter.submitAccountFiling(CONNECTION,
                new AccountFilingSubmission(MediaType.TIKTOK, "creator-1", "Creator One",
                        "https://www.tiktok.com/@creator-1"));
        server.verify();
    }

    @Test
    @DisplayName("查询状态映射为审核中、已加白和已失败")
    void queryMapsRemoteStatus() {
        for (int remoteStatus = 0; remoteStatus <= 2; remoteStatus++) {
            server.expect(requestTo("https://goodshort.test/open/filing/query"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"status\":0,\"success\":true,\"data\":"
                                    + "{\"status\":" + remoteStatus + ",\"filingTime\":\"2025-08-28T11:26:18.000+0000\","
                                    + "\"operateTime\":\"2025-08-28T12:52:36.000+0000\"}}",
                            org.springframework.http.MediaType.APPLICATION_JSON));
        }
        for (int remoteStatus = 0; remoteStatus <= 2; remoteStatus++) {
            assertThat(adapter.queryAccountFiling(CONNECTION,
                    new AccountFilingQuery(MediaType.FACEBOOK, "creator-2")).status())
                    .isEqualTo(remoteStatus == 0 ? FilingStatus.PENDING
                            : remoteStatus == 1 ? FilingStatus.APPROVED : FilingStatus.FAILED);
        }
        server.verify();
    }

    @Test
    @DisplayName("平台暂时不可用和明确拒绝使用可区分异常")
    void remoteFailuresAreClassified() {
        server.expect(requestTo("https://goodshort.test/open/filing/report"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> adapter.submitAccountFiling(CONNECTION,
                new AccountFilingSubmission(MediaType.TIKTOK, "creator-1", null, null)))
                .isInstanceOf(ProviderTransientException.class);
    }

    private Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        return parameters;
    }
}
