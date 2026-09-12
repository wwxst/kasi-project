package com.kasi.backend.provider.mapper;

import com.kasi.backend.provider.entity.ShortDramaConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShortDramaConnectionMapper {
    ShortDramaConnection findById(@Param("id") Long id);
    ShortDramaConnection lockById(@Param("id") Long id);
    ShortDramaConnection findByProviderId(@Param("providerId") Long providerId);
    ShortDramaConnection lockByProviderId(@Param("providerId") Long providerId);
    int insert(ShortDramaConnection connection);
    int update(ShortDramaConnection connection);
}
