package com.kasi.backend.provider.mapper;

import com.kasi.backend.provider.entity.ShortDramaConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShortDramaConnectionMapper {
    ShortDramaConnection findByProviderId(@Param("providerId") Long providerId);
    int insert(ShortDramaConnection connection);
    int update(ShortDramaConnection connection);
}
