package com.kasi.backend.admin.service;

import com.kasi.backend.admin.dto.AdminPageQueryDTO;
import com.kasi.backend.admin.dto.CreateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminDTO;
import com.kasi.backend.admin.dto.UpdateAdminStatusDTO;
import com.kasi.backend.admin.dto.ResetAdminPasswordDTO;
import com.kasi.backend.admin.vo.AdminDetailVO;
import com.kasi.backend.admin.vo.AdminPageVO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminManagementService {

    AdminPageVO getPage(AdminPageQueryDTO query);

    AdminDetailVO getById(Long id);

    AdminDetailVO create(Long operatorId, CreateAdminDTO request);

    AdminDetailVO update(Long operatorId, Long targetId, UpdateAdminDTO request);

    void updateStatus(Long operatorId, Long targetId, UpdateAdminStatusDTO request);

    void resetPassword(Long operatorId, Long targetId, ResetAdminPasswordDTO request);

    AdminDetailVO updateAvatar(Long operatorId, Long targetId, MultipartFile file);

    void delete(Long operatorId, Long targetId);
}
