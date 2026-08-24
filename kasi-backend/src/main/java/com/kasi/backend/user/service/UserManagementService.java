package com.kasi.backend.user.service;

import com.kasi.backend.user.dto.*;
import com.kasi.backend.user.vo.UserDetailVO;
import com.kasi.backend.user.vo.UserPageVO;

public interface UserManagementService {
    UserPageVO getPage(UserPageQueryDTO query);
    UserDetailVO getById(Long id);
    UserDetailVO create(CreateUserDTO request);
    UserDetailVO update(Long id, UpdateUserDTO request);
    void updateStatus(Long id, UpdateUserStatusDTO request);
    void resetPassword(Long id, ResetUserPasswordDTO request);
    void delete(Long id);
}
