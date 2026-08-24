package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.UpdateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminStatusDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.dto.ResetAdminPasswordDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.impl.AdminManagementServiceImpl;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("管理员管理服务")
class AdminManagementServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("登录标识变更时先冻结Redis会话再更新数据库")
    void updateIdentifierBeginsSessionMutationBeforeDatabaseWrite() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser target = new SysAdminUser();
        target.setId(2L);
        target.setUsername("operator");
        target.setRealName("运营管理员");
        target.setStatus(1);
        target.setIsSuperAdmin(0);
        when(mapper.findByIdForUpdate(2L)).thenReturn(target);
        when(mapper.updateProfile(any(SysAdminUser.class))).thenReturn(1);
        when(mapper.findById(2L)).thenReturn(target);
        when(sessionService.beginMutation(SubjectType.ADMIN, 2L))
                .thenReturn(new SessionMutation(SubjectType.ADMIN, 2L, "nonce"));
        UpdateAdminDTO request = new UpdateAdminDTO();
        request.setUsername("operator2");
        request.setRealName("运营管理员");
        TransactionSynchronizationManager.initSynchronization();

        service.update(1L, 2L, request);

        var order = inOrder(sessionService, mapper);
        order.verify(sessionService).beginMutation(SubjectType.ADMIN, 2L);
        order.verify(mapper).updateProfile(any(SysAdminUser.class));
    }

    @Test
    @DisplayName("状态变更时先冻结Redis会话再更新数据库")
    void updateStatusBeginsSessionMutationBeforeDatabaseWrite() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser target = new SysAdminUser();
        target.setId(2L);
        target.setIsSuperAdmin(0);
        when(mapper.findByIdForUpdate(2L)).thenReturn(target);
        when(mapper.updateStatus(2L, 0)).thenReturn(1);
        when(sessionService.beginMutation(SubjectType.ADMIN, 2L))
                .thenReturn(new SessionMutation(SubjectType.ADMIN, 2L, "nonce"));
        UpdateAdminStatusDTO request = new UpdateAdminStatusDTO();
        request.setStatus(0);
        TransactionSynchronizationManager.initSynchronization();

        service.updateStatus(1L, 2L, request);

        var order = inOrder(sessionService, mapper);
        order.verify(sessionService).beginMutation(SubjectType.ADMIN, 2L);
        order.verify(mapper).updateStatus(2L, 0);
    }

    @Test
    @DisplayName("物理删除时先冻结Redis会话再删除数据库记录")
    void deleteBeginsSessionMutationBeforeDatabaseDelete() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser target = new SysAdminUser();
        target.setId(2L);
        target.setIsSuperAdmin(0);
        when(mapper.findByIdForUpdate(2L)).thenReturn(target);
        when(mapper.deleteOrdinaryById(2L)).thenReturn(1);
        when(sessionService.beginMutation(SubjectType.ADMIN, 2L))
                .thenReturn(new SessionMutation(SubjectType.ADMIN, 2L, "nonce"));
        TransactionSynchronizationManager.initSynchronization();

        service.delete(1L, 2L);

        var order = inOrder(sessionService, mapper);
        order.verify(sessionService).beginMutation(SubjectType.ADMIN, 2L);
        order.verify(mapper).deleteOrdinaryById(2L);
    }

    @Test
    @DisplayName("Redis冻结失败时所有敏感数据库写操作均不执行")
    void beginMutationFailurePreventsSensitiveDatabaseWrites() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser target = new SysAdminUser();
        target.setId(2L);
        target.setUsername("operator");
        target.setRealName("运营管理员");
        target.setIsSuperAdmin(0);
        when(mapper.findByIdForUpdate(2L)).thenReturn(target);
        when(sessionService.beginMutation(SubjectType.ADMIN, 2L))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        UpdateAdminDTO update = new UpdateAdminDTO();
        update.setUsername("operator2");
        update.setRealName("运营管理员");
        UpdateAdminStatusDTO status = new UpdateAdminStatusDTO();
        status.setStatus(0);
        ResetAdminPasswordDTO password = new ResetAdminPasswordDTO();
        password.setNewPassword("newpass123");
        password.setConfirmPassword("newpass123");

        assertThrows(IllegalStateException.class, () -> service.update(1L, 2L, update));
        assertThrows(IllegalStateException.class, () -> service.updateStatus(1L, 2L, status));
        assertThrows(IllegalStateException.class, () -> service.resetPassword(1L, 2L, password));
        assertThrows(IllegalStateException.class, () -> service.delete(1L, 2L));

        verify(mapper, never()).updateProfile(any(SysAdminUser.class));
        verify(mapper, never()).updateStatus(2L, 0);
        verify(mapper, never()).updatePassword(any(), any(), any());
        verify(mapper, never()).deleteOrdinaryById(2L);
    }

    @Test
    @DisplayName("数据库写失败后不恢复Redis会话版本")
    void databaseFailureDoesNotCompleteSessionMutation() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser target = new SysAdminUser();
        target.setId(2L);
        target.setIsSuperAdmin(0);
        SessionMutation mutation = new SessionMutation(SubjectType.ADMIN, 2L, "nonce");
        when(mapper.findByIdForUpdate(2L)).thenReturn(target);
        when(sessionService.beginMutation(SubjectType.ADMIN, 2L)).thenReturn(mutation);
        when(mapper.updateStatus(2L, 0)).thenThrow(new IllegalStateException("database failure"));
        UpdateAdminStatusDTO request = new UpdateAdminStatusDTO();
        request.setStatus(0);

        assertThrows(IllegalStateException.class, () -> service.updateStatus(1L, 2L, request));

        verify(sessionService, never()).completeMutation(any(SessionMutation.class));
    }

    @Test
    @DisplayName("并发新增账号唯一键冲突转换为管理员业务错误")
    void createDuplicateKeyRaceReturnsBusinessError() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionService sessionService = mock(SessionService.class);
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(
                mapper, passwordEncoder, sessionService);
        SysAdminUser existing = new SysAdminUser();
        existing.setId(3L);
        when(mapper.findByUsername("finance1")).thenReturn(null, existing);
        when(passwordEncoder.encode("password1")).thenReturn("encoded");
        when(mapper.insert(any(SysAdminUser.class))).thenThrow(new DuplicateKeyException("duplicate"));
        CreateAdminDTO request = new CreateAdminDTO();
        request.setUsername("finance1");
        request.setPassword("password1");
        request.setConfirmPassword("password1");
        request.setRealName("财务管理员");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.create(1L, request));

        assertThat(exception.getCode()).isEqualTo(2007);
    }
}
