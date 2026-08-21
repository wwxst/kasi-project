package com.kasi.backend.provider.mapper;

import com.kasi.backend.provider.entity.ShortDramaProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShortDramaProviderMapper {
    List<ShortDramaProvider> findAll();
    ShortDramaProvider findById(@Param("id") Long id);
    ShortDramaProvider findByIdForUpdate(@Param("id") Long id);
    ShortDramaProvider findByCode(@Param("providerCode") String providerCode);
}
