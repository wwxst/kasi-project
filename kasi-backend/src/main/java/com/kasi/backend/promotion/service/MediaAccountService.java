package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.vo.MediaAccountDetailVO;
import com.kasi.backend.promotion.vo.MediaAccountVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;

import java.util.List;

public interface MediaAccountService {
    List<MediaAccountVO> getMine(Long userId);
    MediaAccountDetailVO getMineById(Long userId, Long id);
    MediaAccountDetailVO create(Long userId, CreateMediaAccountDTO request);
    MediaFilingVO retryFailedSubmission(Long id, Long providerId);
}
