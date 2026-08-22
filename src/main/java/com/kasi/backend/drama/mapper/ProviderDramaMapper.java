package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderDramaMapper {
    ProviderDrama findById(@Param("id") Long id);
    ProviderDrama findByConnectionAndExternalId(@Param("connectionId") Long connectionId, @Param("externalDramaId") String externalDramaId);
    int upsert(ProviderDrama drama);
    long count(@Param("connectionId") Long connectionId, @Param("title") String title, @Param("language") String language,
               @Param("remoteShowStatus") String remoteShowStatus, @Param("localStatus") DramaLocalStatus localStatus);
    List<ProviderDrama> page(@Param("connectionId") Long connectionId, @Param("title") String title, @Param("language") String language,
                             @Param("remoteShowStatus") String remoteShowStatus, @Param("localStatus") DramaLocalStatus localStatus,
                             @Param("offset") int offset, @Param("size") int size);
    List<ProviderDramaContent> findContents(@Param("dramaId") Long dramaId);
    int upsertContent(ProviderDramaContent content);
    int updateLocalStatus(@Param("id") Long id, @Param("localStatus") DramaLocalStatus localStatus);
    long countPublished();
    List<ProviderDrama> pagePublished(@Param("offset") int offset, @Param("size") int size);
}
