package com.kasi.backend.drama.service.impl;

import com.kasi.backend.drama.config.DramaSyncProperties;
import com.kasi.backend.drama.service.DramaLanguageService;
import com.kasi.backend.drama.vo.DramaLanguageOptionVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaLanguageServiceImpl implements DramaLanguageService {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("ENGLISH", "英语"),
            Map.entry("SPANISH", "西班牙语"),
            Map.entry("PORTUGUESE", "葡萄牙语"),
            Map.entry("DEUTSCH", "德语"),
            Map.entry("FRENCH", "法语"),
            Map.entry("BAHASA_INDONESIA", "印度尼西亚语"),
            Map.entry("KOREAN", "韩语"),
            Map.entry("ARAB", "阿拉伯语"),
            Map.entry("THAI", "泰语"),
            Map.entry("JAPANESE", "日语"),
            Map.entry("TRADITIONAL_CHINESE", "中文（繁体）"),
            Map.entry("POLISH", "波兰语"),
            Map.entry("TURKISH", "土耳其语"));

    private final DramaSyncProperties properties;

    public DramaLanguageServiceImpl(DramaSyncProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DramaLanguageOptionVO> listOptions() {
        return properties.getLanguages().stream()
                .map(value -> new DramaLanguageOptionVO(value, labelOf(value)))
                .toList();
    }

    @Override
    public String labelOf(String language) {
        return LABELS.getOrDefault(language, language);
    }
}
