package com.kasi.backend.user.mapper;

import com.kasi.backend.user.entity.PromotionUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 推广用户 Mapper
 */
@Mapper
public interface PromotionUserMapper {

    PromotionUser findById(@Param("id") Long id);

    PromotionUser findByUsername(@Param("username") String username);

    PromotionUser findByMobile(@Param("mobile") String mobile);

    PromotionUser findByEmail(@Param("email") String email);

    PromotionUser findByUserNo(@Param("userNo") String userNo);

    /**
     * 根据account模糊匹配 username / mobile / email
     */
    PromotionUser findByAccount(@Param("account") String account);

    PromotionUser findByAccountForUpdate(@Param("account") String account);

    PromotionUser findByIdForUpdate(@Param("id") Long id);

    int insert(PromotionUser user);

    int updatePassword(@Param("id") Long id,
                       @Param("password") String password);

    int updateLastLogin(@Param("id") Long id,
                        @Param("lastLoginAt") LocalDateTime lastLoginAt,
                        @Param("lastLoginIp") String lastLoginIp);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateUserNo(@Param("id") Long id, @Param("userNo") String userNo, @Param("nickname") String nickname);
}
