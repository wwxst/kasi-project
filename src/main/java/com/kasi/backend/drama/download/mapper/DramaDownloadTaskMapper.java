package com.kasi.backend.drama.download.mapper;

import com.kasi.backend.drama.download.entity.DramaDownloadTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DramaDownloadTaskMapper {
    DramaDownloadTask findById(@Param("id") Long id);
    DramaDownloadTask findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
    int insert(DramaDownloadTask task);
    int markRunning(@Param("id") Long id);
    int updateProgress(@Param("id") Long id, @Param("completedCount") int completedCount);
    int markSuccess(@Param("id") Long id, @Param("filePath") String filePath, @Param("fileName") String fileName);
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);
    int deleteExpired(@Param("now") LocalDateTime now);
    List<DramaDownloadTask> findExpired(@Param("now") LocalDateTime now);
}
