package com.kasi.backend.user.mapper;

import com.kasi.backend.user.entity.PromotionUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推广用户 Mapper
 */
@Mapper
public interface PromotionUserMapper {

    PromotionUser findById(@Param("id") Long id);

    PromotionUser findByUserNo(@Param("userNo") String userNo);

    PromotionUser findByMobile(@Param("mobile") String mobile);

    PromotionUser findByEmail(@Param("email") String email);

    /**
     * 根据 account 匹配手机号或邮箱
     */
    PromotionUser findByAccount(@Param("account") String account);

    PromotionUser findByAccountForUpdate(@Param("account") String account);

    PromotionUser findByIdForUpdate(@Param("id") Long id);

    long countByKeyword(@Param("keyword") String keyword);

    List<PromotionUser> findPage(@Param("keyword") String keyword,
                                 @Param("offset") long offset,
                                 @Param("size") int size);

    int insert(PromotionUser user);

    int updatePassword(@Param("id") Long id,
                       @Param("password") String password);

    int updateLastLogin(@Param("id") Long id,
                        @Param("lastLoginAt") LocalDateTime lastLoginAt,
                        @Param("lastLoginIp") String lastLoginIp);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateProfile(PromotionUser user);

    int updateSelfProfile(@Param("id") Long id,
                          @Param("nickname") String nickname,
                          @Param("realName") String realName);

    int updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    int deleteById(@Param("id") Long id);
}
