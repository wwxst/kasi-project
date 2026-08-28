package com.kasi.backend.drama.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DramaMediaUrlValidatorTest {
    private final DramaMediaUrlValidator validator =
            new DramaMediaUrlValidator("cdn.goodshort.test,media.goodshort.test");

    @Test
    @DisplayName("允许配置域名下的HTTPS媒体地址")
    void allowsConfiguredHttpsHost() {
        assertThat(validator.isAllowed("https://cdn.goodshort.test/path/1.m3u8")).isTrue();
        assertThat(validator.isAllowed("https://a.cdn.goodshort.test/path/1.m3u8")).isTrue();
    }

    @Test
    @DisplayName("拒绝内网地址、用户信息、非标准端口和未配置域名")
    void rejectsUnsafeMediaUrls() {
        assertThat(validator.isAllowed("http://127.0.0.1/secret")).isFalse();
        assertThat(validator.isAllowed("http://10.0.0.1/secret")).isFalse();
        assertThat(validator.isAllowed("https://user:pass@cdn.goodshort.test/1.m3u8")).isFalse();
        assertThat(validator.isAllowed("https://cdn.goodshort.test:8443/1.m3u8")).isFalse();
        assertThat(validator.isAllowed("https://example.com/1.m3u8")).isFalse();
    }
}
