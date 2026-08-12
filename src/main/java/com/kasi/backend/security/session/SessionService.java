package com.kasi.backend.security.session;

import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.context.AuthContext;

public interface SessionService {

    AuthSession createSession(SubjectType subjectType, Long subjectId);

    boolean isValid(AuthContext context);

    void revokeSession(String jti);

    void rotateSessionVersion(SubjectType subjectType, Long subjectId);

    SessionMutation beginMutation(SubjectType subjectType, Long subjectId);

    void completeMutation(SessionMutation mutation);
}
