package com.kasi.backend;

import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.admin.service.impl.AdminAuthServiceImpl;
import com.kasi.backend.auth.password.PasswordResetTokenService;
import com.kasi.backend.auth.password.impl.PasswordResetTokenServiceImpl;
import com.kasi.backend.auth.verification.VerificationCodeService;
import com.kasi.backend.auth.verification.impl.VerificationCodeServiceImpl;
import com.kasi.backend.security.session.SessionService;
import com.kasi.backend.security.session.impl.SessionServiceImpl;
import com.kasi.backend.security.token.TokenService;
import com.kasi.backend.security.token.impl.TokenServiceImpl;
import com.kasi.backend.user.service.UserAuthService;
import com.kasi.backend.user.service.impl.UserAuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceImplementationStructureTest extends BaseAuthTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("认证服务使用接口并各自注入唯一 ServiceImpl")
    void authServicesUseInterfacesWithSingleImplementations() {
        assertThat(AdminAuthService.class).isInterface();
        assertThat(UserAuthService.class).isInterface();
        assertThat(applicationContext.getBean(AdminAuthService.class))
                .isInstanceOf(AdminAuthServiceImpl.class);
        assertThat(applicationContext.getBean(UserAuthService.class))
                .isInstanceOf(UserAuthServiceImpl.class);
        assertThat(VerificationCodeService.class).isInterface();
        assertThat(applicationContext.getBean(VerificationCodeService.class))
                .isInstanceOf(VerificationCodeServiceImpl.class);
        assertThat(PasswordResetTokenService.class).isInterface();
        assertThat(applicationContext.getBean(PasswordResetTokenService.class))
                .isInstanceOf(PasswordResetTokenServiceImpl.class);
        assertThat(SessionService.class).isInterface();
        assertThat(applicationContext.getBean(SessionService.class))
                .isInstanceOf(SessionServiceImpl.class);
        assertThat(TokenService.class).isInterface();
        assertThat(applicationContext.getBean(TokenService.class))
                .isInstanceOf(TokenServiceImpl.class);
    }
}
