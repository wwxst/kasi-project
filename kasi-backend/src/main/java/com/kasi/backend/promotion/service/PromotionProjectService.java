package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionProjectPageQueryDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectDTO;
import com.kasi.backend.promotion.vo.PromotionProjectPageVO;
import com.kasi.backend.promotion.vo.PromotionProjectCardVO;
import com.kasi.backend.promotion.vo.PromotionProjectVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PromotionProjectService {
    PromotionProjectPageVO getPage(PromotionProjectPageQueryDTO query);

    PromotionProjectVO getById(Long id);

    PromotionProjectVO create(UpsertPromotionProjectDTO request, MultipartFile coverFile);

    PromotionProjectVO update(Long id, UpsertPromotionProjectDTO request, MultipartFile coverFile);

    void delete(Long id);

    List<PromotionProjectCardVO> listEnabled();
}
