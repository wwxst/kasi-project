package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;

public interface MediaAccountAdminService {
    AdminMediaAccountPageVO getPage(AdminMediaAccountPageQueryDTO query);
    AdminMediaAccountDetailVO getById(Long id);
    MediaFilingVO retry(Long id, Long providerId);
    void delete(Long id);
}
