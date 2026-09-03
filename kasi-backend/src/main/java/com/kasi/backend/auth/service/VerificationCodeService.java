package com.kasi.backend.auth.service;

import com.kasi.backend.common.enums.VerificationScene;

public interface VerificationCodeService {

    void sendVerificationCode(String target, VerificationScene scene);

    void reserveVerificationCode(String target, VerificationScene scene);

    boolean verifyCode(String target, VerificationScene scene, String code);
}
