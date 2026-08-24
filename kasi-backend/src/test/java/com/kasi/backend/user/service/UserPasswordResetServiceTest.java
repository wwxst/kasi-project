package com.kasi.backend.user.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.auth.entity.PasswordResetTokenReservation;
import com.kasi.backend.auth.service.PasswordResetTokenService;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.user.dto.ResetPasswordDTO;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("密码重置事务边界")
class UserPasswordResetServiceTest extends BaseAuthTest {

    @MockitoBean
    private PromotionUserMapper promotionUserMapper;

    @MockitoBean
    private PasswordResetTokenService passwordResetTokenService;

    @MockitoBean
    private SessionService sessionService;

    @Test
    @DisplayName("用户不存在时恢复READY供明确失败重试")
    void resetPasswordWhenUserMissingRestoresReady() {
        PasswordResetTokenReservation reservation = reservation();
        when(passwordResetTokenService.reserveToken("token")).thenReturn(reservation);
        when(promotionUserMapper.findByIdForUpdate(7L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userAuthService().resetPassword(request()));

        verify(passwordResetTokenService).restoreReady(reservation);
        verify(promotionUserMapper).findByIdForUpdate(7L);
        verify(promotionUserMapper, never()).updatePassword(anyLong(), any());
        verify(sessionService, never()).rotateSessionVersion(any(), anyLong());
    }

    @Test
    @DisplayName("数据库结果不确定异常保持PROCESSING不可重放")
    void resetPasswordWhenDatabaseResultUnknownKeepsProcessing() {
        PasswordResetTokenReservation reservation = reservation();
        when(passwordResetTokenService.reserveToken("token")).thenReturn(reservation);
        when(promotionUserMapper.findByIdForUpdate(7L)).thenReturn(
                org.mockito.Mockito.mock(com.kasi.backend.user.entity.PromotionUser.class));
        when(sessionService.beginMutation(SubjectType.USER, 7L))
                .thenReturn(new SessionMutation(SubjectType.USER, 7L, "nonce"));
        when(promotionUserMapper.updatePassword(anyLong(), any()))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> userAuthService().resetPassword(request()));

        verify(sessionService).beginMutation(SubjectType.USER, 7L);
        verify(passwordResetTokenService, never()).restoreReady(reservation);
        verify(passwordResetTokenService, never()).completeToken(reservation);
    }

    private UserAuthService userAuthService() {
        return webApplicationContext.getBean(UserAuthService.class);
    }

    private PasswordResetTokenReservation reservation() {
        return new PasswordResetTokenReservation(7L, SubjectType.USER, "hash");
    }

    private ResetPasswordDTO request() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setResetToken("token");
        request.setNewPassword("newpassword");
        request.setConfirmPassword("newpassword");
        return request;
    }
}
