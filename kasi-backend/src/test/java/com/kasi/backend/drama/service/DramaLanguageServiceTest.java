package com.kasi.backend.drama.service;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.impl.DramaLanguageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("短剧语言服务")
class DramaLanguageServiceTest {
    @Test
    @DisplayName("语言选项使用实际生效配置并返回中文名称")
    void listOptionsUsesEffectiveConfiguredLanguages() {
        DramaSyncProperties properties = new DramaSyncProperties();
        properties.setLanguages(List.of("JAPANESE", "THAI"));
        DramaLanguageService service = new DramaLanguageServiceImpl(properties);

        assertThat(service.listOptions())
                .extracting(option -> List.of(option.value(), option.label()))
                .containsExactly(
                        List.of("JAPANESE", "日语"),
                        List.of("THAI", "泰语"));
    }
}
