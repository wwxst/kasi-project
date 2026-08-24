package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromotionTaskMapper {
    PromotionTask findByRequestAndMedia(@Param("userId") Long userId, @Param("requestKey") String requestKey,
                                        @Param("mediaType") String mediaType);

    int insert(PromotionTask task);

    long countByUser(@Param("userId") Long userId, @Param("taskName") String taskName,
                     @Param("dramaTitle") String dramaTitle, @Param("mediaType") String mediaType);

    List<PromotionTask> pageByUser(@Param("userId") Long userId, @Param("taskName") String taskName,
                                   @Param("dramaTitle") String dramaTitle, @Param("mediaType") String mediaType,
                                   @Param("offset") int offset, @Param("size") int size);
}
