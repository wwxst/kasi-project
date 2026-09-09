package com.kasi.backend.promotion.service;

import org.springframework.web.multipart.MultipartFile;

public interface PromotionProjectCoverStorageService {
    String store(MultipartFile file);

    void deleteIfLocal(String coverImageUrl);
}
