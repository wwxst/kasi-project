package com.kasi.backend.security.service;

import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.context.AuthContext;

public interface TokenService {

    String generateToken(Long subjectId, SubjectType subjectType, String username,
                         String jti, String sessionVersion);

    AuthContext parseToken(String token);
}
