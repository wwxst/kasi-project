package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionProjectType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromotionProjectTypeMapper {
    List<PromotionProjectType> findAll();

    PromotionProjectType findById(@Param("id") Long id);

    PromotionProjectType findByIdForUpdate(@Param("id") Long id);

    PromotionProjectType findByCode(@Param("code") String code);

    int insert(PromotionProjectType projectType);

    int update(PromotionProjectType projectType);

    int deleteById(@Param("id") Long id);
}
