package com.kasi.backend.sms.service;

import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.sms.dto.UpdateSmsConfigDTO;
import com.kasi.backend.sms.entity.SmsRuntimeConfig;
import com.kasi.backend.sms.vo.SmsConfigVO;

public interface SmsConfigService {

    SmsConfigVO getConfig();

    SmsConfigVO update(Long adminId, UpdateSmsConfigDTO request);

    SmsRuntimeConfig requireRuntimeConfig(VerificationScene scene);
}
