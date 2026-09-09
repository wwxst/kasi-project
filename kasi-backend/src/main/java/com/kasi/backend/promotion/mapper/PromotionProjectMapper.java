package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromotionProjectMapper {
    PromotionProject findById(@Param("id") Long id);

    long countAll();

    List<PromotionProject> findPage(@Param("offset") int offset, @Param("size") int size);

    List<PromotionProject> findEnabled();

    int insert(PromotionProject project);

    int update(PromotionProject project);

    int deleteById(@Param("id") Long id);
}
