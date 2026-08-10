package com.kasi.backend.admin.mapper;

import com.kasi.backend.admin.entity.SysAdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 后台管理员 Mapper
 */
@Mapper
public interface SysAdminUserMapper {

    SysAdminUser findById(@Param("id") Long id);

    SysAdminUser findByUsername(@Param("username") String username);

    SysAdminUser findByMobile(@Param("mobile") String mobile);

    SysAdminUser findByEmail(@Param("email") String email);

    /**
     * 根据account模糊匹配 username / mobile / email
     */
    SysAdminUser findByAccount(@Param("account") String account);

    int insert(SysAdminUser user);

    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("passwordChangedAt") LocalDateTime passwordChangedAt);

    int updateLastLogin(@Param("id") Long id,
                        @Param("lastLoginAt") LocalDateTime lastLoginAt,
                        @Param("lastLoginIp") String lastLoginIp);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
