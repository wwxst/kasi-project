package com.kasi.backend.user.service;

import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarStorageService {

    String store(MultipartFile file);

    void deleteIfLocal(String avatarUrl);
}
