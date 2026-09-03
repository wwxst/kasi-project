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
    boolean needsContentSync(@Param("dramaId") Long dramaId);
    List<Long> findContentSyncCandidateIds(@Param("providerId") Long providerId,
                                           @Param("language") String language,
                                           @Param("missingOnly") boolean missingOnly,
                                           @Param("afterId") Long afterId,
                                           @Param("limit") int limit);
    int updateLocalStatus(@Param("id") Long id, @Param("localStatus") DramaLocalStatus localStatus);
    int updatePromotionMetadata(@Param("id") Long id, @Param("commissionScope") String commissionScope,
                                @Param("promotionDescription") String promotionDescription);
    long countPublished();
    List<ProviderDrama> pagePublished(@Param("offset") int offset, @Param("size") int size);
}
