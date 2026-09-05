package com.kasi.backend.drama.service;

import com.kasi.backend.drama.vo.DramaLanguageOptionVO;

import java.util.List;

public interface DramaLanguageService {
    List<DramaLanguageOptionVO> listOptions();

    String labelOf(String language);
}
