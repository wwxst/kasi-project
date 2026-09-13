package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.UpsertPromotionProjectTypeDTO;
import com.kasi.backend.promotion.vo.PromotionProjectTypeVO;

import java.util.List;

public interface PromotionProjectTypeService {
    List<PromotionProjectTypeVO> list();

    PromotionProjectTypeVO create(UpsertPromotionProjectTypeDTO request);

    PromotionProjectTypeVO update(Long id, UpsertPromotionProjectTypeDTO request);

    void delete(Long id);
}
