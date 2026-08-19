package com.kasi.backend.provider.mapper;

import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.FilingMode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShortDramaConnectionMapper {
    ShortDramaConnection findById(@Param("id") Long id);
    ShortDramaConnection findByProviderId(@Param("providerId") Long providerId);
    int insert(ShortDramaConnection connection);
    int update(ShortDramaConnection connection);
    int updateFilingMode(@Param("connectionId") Long connectionId,
                         @Param("filingMode") FilingMode filingMode,
                         @Param("updatedBy") Long updatedBy);
}
