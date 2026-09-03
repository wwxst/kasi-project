package com.kasi.backend.drama.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DramaMediaUrlValidatorTest {
    private final DramaMediaUrlValidator validator = new DramaMediaUrlValidator();

    @Test
    @DisplayName("允许配置域名下的HTTPS媒体地址")
    void allowsConfiguredHttpsHost() {
        assertThat(validator.isAllowed("https://novelopen.com/1.m3u8", "novelopen.com")).isTrue();
        assertThat(validator.isAllowed("https://v-koc.novelopen.com/1.m3u8", "novelopen.com")).isTrue();
        assertThat(validator.isAllowed("https://a.b.novelopen.com/1.m3u8", "NovelOpen.COM")).isTrue();
    }

    @Test
    @DisplayName("拒绝内网地址、用户信息、非标准端口和未配置域名")
    void rejectsUnsafeMediaUrls() {
        assertThat(validator.isAllowed("http://127.0.0.1/secret", "127.0.0.1")).isFalse();
        assertThat(validator.isAllowed("http://10.0.0.1/secret", "10.0.0.1")).isFalse();
        assertThat(validator.isAllowed("https://user:pass@v-koc.novelopen.com/1.m3u8", "novelopen.com")).isFalse();
        assertThat(validator.isAllowed("https://v-koc.novelopen.com:8443/1.m3u8", "novelopen.com")).isFalse();
        assertThat(validator.isAllowed("https://evilnovelopen.com/1.m3u8", "novelopen.com")).isFalse();
        assertThat(validator.isAllowed("https://novelopen.com.evil.com/1.m3u8", "novelopen.com")).isFalse();
        assertThat(validator.isAllowed("https://v-koc.novelopen.com/1.m3u8", null)).isFalse();
    }
}
