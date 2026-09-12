package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.dto.UpdateManualFilingStatusDTO;
import com.kasi.backend.promotion.enums.SubmissionResolution;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;

public interface MediaAccountAdminService {
    AdminMediaAccountPageVO getPage(AdminMediaAccountPageQueryDTO query);
    byte[] exportXlsx(AdminMediaAccountPageQueryDTO query);
    AdminMediaAccountDetailVO getById(Long id);
    MediaFilingVO retry(Long id, Long providerId);
    MediaFilingVO updateManualStatus(Long operatorId, Long id, Long providerId,
                                     UpdateManualFilingStatusDTO request);
    MediaFilingVO resolveSubmission(Long operatorId, Long id, Long providerId,
                                    SubmissionResolution resolution);
    void delete(Long id);
}
