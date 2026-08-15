package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.UpdateAdminProfileDTO;
import com.kasi.backend.admin.entity.SysAdminUser;
import com.kasi.backend.admin.mapper.SysAdminUserMapper;
import com.kasi.backend.admin.service.impl.AdminAuthServiceImpl;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.security.service.SessionService;
import com.kasi.backend.security.service.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("管理员本人资料服务")
class AdminAuthServiceTest {

    @Test
    @DisplayName("本人资料并发账号冲突转换为管理员业务错误")
    void updateProfileDuplicateKeyRaceReturnsBusinessError() {
        SysAdminUserMapper mapper = mock(SysAdminUserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TokenService tokenService = mock(TokenService.class);
        SessionService sessionService = mock(SessionService.class);
        AdminAuthServiceImpl service = new AdminAuthServiceImpl(
                mapper, passwordEncoder, tokenService, sessionService,
                mock(AdminAvatarStorageService.class));
        SysAdminUser current = new SysAdminUser();
        current.setId(1L);
        current.setUsername("kasiadmin");
        SysAdminUser existing = new SysAdminUser();
        existing.setId(2L);
        when(mapper.findByIdForUpdate(1L)).thenReturn(current);
        when(mapper.findByUsername("kasiadmin2")).thenReturn(null, existing);
        when(mapper.updateProfile(any(SysAdminUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        UpdateAdminProfileDTO request = new UpdateAdminProfileDTO();
        request.setUsername("kasiadmin2");
        request.setRealName("系统管理员");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.updateProfile(1L, request));

        assertThat(exception.getCode()).isEqualTo(2007);
    }
}
