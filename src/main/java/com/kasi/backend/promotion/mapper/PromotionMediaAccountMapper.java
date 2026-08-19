package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.enums.MediaType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromotionMediaAccountMapper {
    List<PromotionMediaAccount> findAll();
    PromotionMediaAccount findById(@Param("id") Long id);
    PromotionMediaAccount findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
    PromotionMediaAccount findByIdForUpdate(@Param("id") Long id);
    PromotionMediaAccount findByIdentity(@Param("mediaType") MediaType mediaType,
                                         @Param("externalAccountId") String externalAccountId);
    List<PromotionMediaAccount> findByUserId(@Param("userId") Long userId);
    long countByUserId(@Param("userId") Long userId);
    int insert(PromotionMediaAccount entity);
    int updateDetails(PromotionMediaAccount entity);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
