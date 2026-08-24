package com.kasi.backend.provider.goodshort;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoodShortProperties.class)
public class GoodShortClientConfig {

    @Bean
    public Clock providerClock() {
        return Clock.systemUTC();
    }

    @Bean("goodShortRestClient")
    public RestClient goodShortRestClient(GoodShortProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
