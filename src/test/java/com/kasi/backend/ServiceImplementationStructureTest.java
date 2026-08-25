package com.kasi.backend;

import com.kasi.backend.admin.service.AdminAuthService;
import com.kasi.backend.admin.service.AdminManagementService;
import com.kasi.backend.admin.service.impl.AdminAuthServiceImpl;
import com.kasi.backend.admin.service.impl.AdminManagementServiceImpl;
import com.kasi.backend.auth.service.PasswordResetTokenService;
import com.kasi.backend.auth.service.impl.PasswordResetTokenServiceImpl;
import com.kasi.backend.auth.service.VerificationCodeService;
import com.kasi.backend.auth.service.impl.VerificationCodeServiceImpl;
import com.kasi.backend.promotion.service.PromotionLinkPersistenceService;
import com.kasi.backend.promotion.service.impl.PromotionLinkPersistenceServiceImpl;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.security.service.impl.SessionServiceImpl;
import com.kasi.backend.security.service.TokenService;
import com.kasi.backend.security.service.impl.TokenServiceImpl;
import com.kasi.backend.user.service.UserAuthService;
import com.kasi.backend.user.service.PromotionUserCreationService;
import com.kasi.backend.user.service.impl.PromotionUserCreationServiceImpl;
import com.kasi.backend.user.service.impl.UserAuthServiceImpl;
import com.kasi.backend.user.service.UserManagementService;
import com.kasi.backend.user.service.impl.UserManagementServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        assertThat(AdminManagementService.class).isInterface();
        assertThat(applicationContext.getBean(AdminManagementService.class))
                .isInstanceOf(AdminManagementServiceImpl.class);
        assertThat(applicationContext.getBean(UserAuthService.class))
                .isInstanceOf(UserAuthServiceImpl.class);
        assertThat(UserManagementService.class).isInterface();
        assertThat(applicationContext.getBean(UserManagementService.class))
                .isInstanceOf(UserManagementServiceImpl.class);
        assertThat(PromotionUserCreationService.class).isInterface();
        assertThat(applicationContext.getBean(PromotionUserCreationService.class))
                .isInstanceOf(PromotionUserCreationServiceImpl.class);
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
        assertThat(PromotionLinkPersistenceService.class).isInterface();
        assertThat(applicationContext.getBean(PromotionLinkPersistenceService.class))
                .isInstanceOf(PromotionLinkPersistenceServiceImpl.class);
        for (String method : new String[]{"preparePending", "markSuccess", "markFailed"}) {
            Transactional transactional = findTransactional(method);
            assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        }
    }

    private Transactional findTransactional(String method) {
        return java.util.Arrays.stream(PromotionLinkPersistenceServiceImpl.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .map(candidate -> candidate.getAnnotation(Transactional.class))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }
}
