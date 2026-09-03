package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.ProviderSyncCheckpoint;
import com.kasi.backend.drama.enums.DramaSyncStatus;
import com.kasi.backend.drama.enums.DramaSyncType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderSyncCheckpointMapper {
    ProviderSyncCheckpoint find(@Param("connectionId") Long connectionId, @Param("syncType") DramaSyncType syncType, @Param("language") String language);
    ProviderSyncCheckpoint findById(@Param("id") Long id);
    List<ProviderSyncCheckpoint> findByIds(@Param("ids") List<Long> ids);
    List<ProviderSyncCheckpoint> findByConnectionId(@Param("connectionId") Long connectionId);
    List<ProviderSyncCheckpoint> findActive(@Param("connectionId") Long connectionId,
                                            @Param("language") String language);
    int insert(ProviderSyncCheckpoint checkpoint);
    int requestRun(@Param("id") Long id, @Param("requestedAt") LocalDateTime requestedAt,
                   @Param("restart") boolean restart);
    List<ProviderSyncCheckpoint> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claimLease(@Param("id") Long id, @Param("owner") String owner, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    int updateProgress(@Param("id") Long id, @Param("owner") String owner,
                       @Param("pageNo") int pageNo, @Param("updateTime") Long updateTime,
                       @Param("fetched") int fetched, @Param("upserted") int upserted,
                       @Param("inserted") int inserted, @Param("updated") int updated,
                       @Param("skipped") int skipped, @Param("errors") int errors);
    int markSuccess(@Param("id") Long id, @Param("owner") String owner,
                    @Param("finishedAt") LocalDateTime finishedAt, @Param("pageNo") int pageNo,
                    @Param("updateTime") Long updateTime);
    int markFailure(@Param("id") Long id, @Param("owner") String owner,
                    @Param("finishedAt") LocalDateTime finishedAt,
                    @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);
    int upsert(ProviderSyncCheckpoint checkpoint);
}
