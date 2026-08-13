package com.kasi.backend.user;

import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.AuthStateUnavailableException;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.user.dto.ResetUserPasswordDTO;
import com.kasi.backend.user.dto.UpdateUserDTO;
import com.kasi.backend.user.dto.UpdateUserStatusDTO;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.impl.UserManagementServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("推广用户管理服务")
class UserManagementServiceTest {

    @Mock private PromotionUserMapper promotionUserMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SessionService sessionService;
    @InjectMocks private UserManagementServiceImpl service;

    @Test
    @DisplayName("Redis进入变更状态失败时不更新数据库")
    void updateStatusDoesNotWriteWhenRedisFails() {
        prepareRedisFailure();
        UpdateUserStatusDTO request = new UpdateUserStatusDTO();
        request.setStatus(0);

        assertThatThrownBy(() -> service.updateStatus(1L, request))
                .isInstanceOf(AuthStateUnavailableException.class);
        verify(promotionUserMapper, never()).updateStatus(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Redis进入变更状态失败时不更新联系方式")
    void updateContactDoesNotWriteWhenRedisFails() {
        PromotionUser user = prepareRedisFailure();
        user.setMobile("13800138000");
        UpdateUserDTO request = new UpdateUserDTO();
        request.setMobile("13900139000");
        request.setNickname("推广用户");

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(AuthStateUnavailableException.class);
        verify(promotionUserMapper, never()).updateProfile(any());
    }

    @Test
    @DisplayName("Redis进入变更状态失败时不重置密码")
    void resetPasswordDoesNotWriteWhenRedisFails() {
        prepareRedisFailure();
        ResetUserPasswordDTO request = new ResetUserPasswordDTO();
        request.setNewPassword("newpassword1");
        request.setConfirmPassword("newpassword1");

        assertThatThrownBy(() -> service.resetPassword(1L, request))
                .isInstanceOf(AuthStateUnavailableException.class);
        verify(promotionUserMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("Redis进入变更状态失败时不删除用户")
    void deleteDoesNotWriteWhenRedisFails() {
        prepareRedisFailure();

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(AuthStateUnavailableException.class);
        verify(promotionUserMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("并发邮箱唯一键冲突转换为业务错误")
    void createMapsConcurrentEmailDuplicateToBusinessError() {
        com.kasi.backend.user.dto.CreateUserDTO request = new com.kasi.backend.user.dto.CreateUserDTO();
        request.setEmail("user@example.com");
        request.setNickname("推广用户");
        request.setPassword("password1");
        request.setConfirmPassword("password1");
        when(passwordEncoder.encode("password1")).thenReturn("encoded");
        when(promotionUserMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_email'"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3007);
    }

    private PromotionUser prepareRedisFailure() {
        PromotionUser user = new PromotionUser();
        user.setId(1L);
        when(promotionUserMapper.findByIdForUpdate(1L)).thenReturn(user);
        when(sessionService.beginMutation(SubjectType.USER, 1L))
                .thenThrow(new AuthStateUnavailableException(new RuntimeException("redis unavailable")));
        return user;
    }
}
