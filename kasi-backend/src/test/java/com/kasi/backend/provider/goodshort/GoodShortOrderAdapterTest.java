package com.kasi.backend.provider.goodshort;

import com.kasi.backend.provider.spi.OrderSyncRequest;
import com.kasi.backend.provider.spi.ProviderOrderStatus;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoodShortOrderAdapterTest {
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
    @DisplayName("订单列表按日期分页请求并完整映射已支付和退款订单")
    void fetchOrdersMapsPaidAndRefundedRecords() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("pid", "partner-1");
        parameters.put("timestamp", TIMESTAMP);
        parameters.put("pageNo", 1);
        parameters.put("pageSize", 500);
        parameters.put("startDate", "2025-07-01 00:00:00");
        parameters.put("endDate", "2025-07-01 23:59:59");

        server.expect(requestTo("https://goodshort.test/open/partner/orders"))
                .andExpect(header("sign", signer.sign(parameters, API_KEY)))
                .andExpect(content().json("""
                        {"pid":"partner-1","timestamp":1681810530092,"pageNo":1,"pageSize":500,
                         "startDate":"2025-07-01 00:00:00","endDate":"2025-07-01 23:59:59"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"data":{"records":[
                          {"userId":"20031995","orderId":"order-paid","payMoney":999,
                           "payTime":"2025-07-01 15:55:30","payStatus":1,
                           "customParams":"583104726918","bookId":"book-1","searchCode":"21302",
                           "channelCode":"GRKOCABTT00001","pid":"partner-1","utime":"2025-07-01 16:00:00"},
                          {"userId":"20031996","orderId":"order-refund","payMoney":99,
                           "payTime":"2025-07-01 17:43:41","payStatus":3,
                           "customParams":"731000000042","bookId":"book-2","searchCode":"21303",
                           "channelCode":"GRKOCABFB00002","pid":"partner-1","utime":"2025-07-02 08:00:00"}
                        ],"pageNo":1,"pageSize":500,"pages":1,"total":2},
                         "status":0,"message":"success","success":true}
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.fetchOrders(CONNECTION, new OrderSyncRequest(
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59), 1, 500));

        assertThat(page.records()).hasSize(2);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pages()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.records().getFirst()).satisfies(order -> {
            assertThat(order.externalOrderId()).isEqualTo("order-paid");
            assertThat(order.status()).isEqualTo(ProviderOrderStatus.PAID);
            assertThat(order.rawStatus()).isEqualTo("1");
            assertThat(order.orderAmountMinor()).isEqualTo(999);
            assertThat(order.orderAmount()).isEqualByComparingTo(new BigDecimal("9.99"));
            assertThat(order.currency()).isEqualTo("USD");
            assertThat(order.customParams()).isEqualTo("583104726918");
            assertThat(order.providerUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 7, 1, 16, 0));
            assertThat(order.rawPayloadJson()).contains("\"orderId\":\"order-paid\"");
        });
        assertThat(page.records().get(1).status()).isEqualTo(ProviderOrderStatus.REFUNDED);
        server.verify();
    }

    @Test
    @DisplayName("未知支付状态保留原值且不误判为已支付")
    void fetchOrdersPreservesUnknownStatus() {
        server.expect(requestTo("https://goodshort.test/open/partner/orders"))
                .andRespond(withSuccess("""
                        {"data":{"records":[{"orderId":"order-unknown","payMoney":100,"payStatus":9}],
                         "pageNo":1,"pageSize":500,"pages":1,"total":1},
                         "status":0,"success":true}
                        """, MediaType.APPLICATION_JSON));

        var page = adapter.fetchOrders(CONNECTION, new OrderSyncRequest(
                LocalDateTime.of(2025, 7, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 23, 59, 59), 1, 500));

        assertThat(page.records().getFirst().status()).isEqualTo(ProviderOrderStatus.UNKNOWN);
        assertThat(page.records().getFirst().rawStatus()).isEqualTo("9");
        server.verify();
    }
}
