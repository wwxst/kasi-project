package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.dto.AdminUpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountStatusDTO;
import com.kasi.backend.promotion.vo.MediaAccountDetailVO;
import com.kasi.backend.promotion.vo.MediaAccountVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;

import java.util.List;

public interface MediaAccountService {
    List<MediaAccountVO> getMine(Long userId);
    MediaAccountDetailVO getMineById(Long userId, Long id);
    MediaAccountDetailVO create(Long userId, CreateMediaAccountDTO request);
    MediaAccountDetailVO update(Long userId, Long id, UpdateMediaAccountDTO request);
    MediaAccountDetailVO updateByAdmin(Long id, AdminUpdateMediaAccountDTO request);
    void updateStatus(Long userId, Long id, UpdateMediaAccountStatusDTO request);
    MediaFilingVO submitOrRetry(Long userId, Long id, Long providerId);
}
