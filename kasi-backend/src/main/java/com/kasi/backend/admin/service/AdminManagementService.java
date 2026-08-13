package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.vo.AdminDetailVO;
import com.kasi.backend.admin.vo.AdminPageVO;

public interface AdminManagementService {

    AdminPageVO getPage(AdminPageQueryDTO query);

    AdminDetailVO getById(Long id);

    AdminDetailVO create(Long operatorId, CreateAdminDTO request);
}
