package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.DramaContentSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DramaContentSyncTaskMapper {
    DramaContentSyncTask findById(@Param("id") Long id);
    DramaContentSyncTask findByDramaId(@Param("dramaId") Long dramaId);
    int insert(DramaContentSyncTask task);
    int request(@Param("dramaId") Long dramaId, @Param("requestedAt") LocalDateTime requestedAt);
    List<Long> findDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claimLease(@Param("id") Long id, @Param("owner") String owner,
                   @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    int markSuccess(@Param("id") Long id, @Param("owner") String owner,
                    @Param("totalFetched") int totalFetched,
                    @Param("insertedCount") int insertedCount,
                    @Param("updatedCount") int updatedCount);
    int recordRetry(@Param("id") Long id, @Param("owner") String owner,
                    @Param("nextRunAt") LocalDateTime nextRunAt,
                    @Param("retryCount") int retryCount,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage);
    int markFailed(@Param("id") Long id, @Param("owner") String owner,
                   @Param("retryCount") int retryCount,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);
}
