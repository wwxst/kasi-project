package com.kasi.backend.provider.goodshort;

import com.kasi.backend.provider.spi.AnalyticalReportRequest;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoodShortAnalyticalReportAdapterTest {
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
    @DisplayName("analyticalReport使用500条分页和yyyy-MM-dd并映射日报字段")
    void fetchAnalyticalReportsMapsDailySummary() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        parameters.put("pageNo", 1);
        parameters.put("pageSize", 500);
        parameters.put("startTime", "2026-08-19");
        parameters.put("endTime", "2026-08-19");
        parameters.put("customParams", "100000000001");

        server.expect(requestTo("https://goodshort.test/creek/open/promotion/analyticalReport"))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {"pid":"partner-1","timestamp":1681810530092,"pageNo":1,"pageSize":500,
                         "startTime":"2026-08-19","endTime":"2026-08-19","customParams":"100000000001"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"status":0,"success":true,"data":{"records":[
                          {"reportDate":"2026-08-19","pId":"partner-1","customParams":"100000000001",
                           "bookId":"book-1","code":"CODE1","clickCount":10,"attributedUserCount":8,
                           "newRegisteredUserCount":3,"newPaidUserCount":2,"newMemberUserCount":1,
                           "paidUserCount":4,"orderCount":5,"orderAmount":"12.34"}
                        ],"pageNo":1,"pageSize":500,"pages":1,"total":1}}""", MediaType.APPLICATION_JSON));

        var page = adapter.fetchAnalyticalReports(CONNECTION,
                new AnalyticalReportRequest(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19),
                        1, 500, null, null, "100000000001"));

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().reportDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(page.records().getFirst().orderAmount()).isEqualByComparingTo("12.34");
        assertThat(page.hasNext()).isFalse();
        server.verify();
    }
}
