package com.kasi.backend.admin.service;

import org.springframework.web.multipart.MultipartFile;

public interface AdminAvatarStorageService {

    String store(MultipartFile file);

    void deleteIfLocal(String avatarUrl);
}
