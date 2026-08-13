package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.UpdateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminStatusDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.impl.AdminManagementServiceImpl;
import com.kasi.backend.common.enums.SubjectType;
import com.kasi.backend.security.entity.SessionMutation;
import com.kasi.backend.security.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
