package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PromotionLinkMapper {
    PromotionLink findByUserAndRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey,
                                          @Param("mediaType") String mediaType, @Param("linkVariant") String linkVariant);
    PromotionLink findByUserAndRequestKeyForUpdate(@Param("userId") Long userId, @Param("requestKey") String requestKey,
                                                   @Param("mediaType") String mediaType, @Param("linkVariant") String linkVariant);
    List<PromotionLink> findBatchByUserAndRequestKey(@Param("userId") Long userId, @Param("requestKey") String requestKey);
    long countByUserId(@Param("userId") Long userId);
    List<PromotionLink> findPageByUserId(@Param("userId") Long userId,
                                         @Param("offset") int offset, @Param("size") int size);
    PromotionLink findForOrderAttribution(@Param("connectionId") Long connectionId,
                                          @Param("partnerId") String partnerId,
                                          @Param("externalDramaId") String externalDramaId,
                                          @Param("userNo") String userNo,
                                          @Param("externalCode") String externalCode);
    PromotionLink findSuccessfulByIdentity(@Param("connectionId") Long connectionId,
                                           @Param("dramaId") Long dramaId,
                                           @Param("userId") Long userId,
                                           @Param("externalCode") String externalCode);
    int insert(PromotionLink link);
    int deleteById(@Param("id") Long id);
    int markSuccess(@Param("id") Long id, @Param("externalCode") String externalCode,
                    @Param("shareUrl") String shareUrl);
    int markFailed(@Param("id") Long id, @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);
    int resetPending(@Param("id") Long id, @Param("status") PromotionLinkStatus status,
                     @Param("trackingNo") String trackingNo, @Param("updatedAt") LocalDateTime updatedAt);
}
