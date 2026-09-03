package com.kasi.backend.sms.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.security.context.AuthContextHolder;
import com.kasi.backend.sms.dto.UpdateSmsConfigDTO;
import com.kasi.backend.sms.service.SmsConfigService;
import com.kasi.backend.sms.vo.SmsConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/sms-config")
@RequiredArgsConstructor
public class SmsConfigAdminController {

    private final SmsConfigService smsConfigService;

    @GetMapping
    public ApiResponse<SmsConfigVO> getConfig() {
        return ApiResponse.success(smsConfigService.getConfig());
    }

    @PutMapping
    public ApiResponse<SmsConfigVO> update(@Valid @RequestBody UpdateSmsConfigDTO request) {
        return ApiResponse.success(smsConfigService.update(AuthContextHolder.getAdminId(), request));
    }
}
