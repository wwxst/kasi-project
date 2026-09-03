package com.kasi.backend.drama.mapper;

import com.kasi.backend.drama.entity.DramaSyncDisplayRun;
import com.kasi.backend.drama.entity.DramaSyncDisplayRunItem;
import com.kasi.backend.drama.enums.DramaSyncDomain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DramaSyncDisplayRunMapper {
    int insertRun(DramaSyncDisplayRun run);

    int insertItem(DramaSyncDisplayRunItem item);

    int updateTaskType(@Param("runId") String runId,
                       @Param("taskType") com.kasi.backend.drama.enums.DramaSyncTaskType taskType);

    DramaSyncDisplayRun findById(@Param("runId") String runId,
                                 @Param("providerId") Long providerId,
                                 @Param("domain") DramaSyncDomain domain);

    DramaSyncDisplayRun findChildRun(@Param("parentRunId") String parentRunId,
                                     @Param("domain") DramaSyncDomain domain,
                                     @Param("taskType") com.kasi.backend.drama.enums.DramaSyncTaskType taskType);

    List<DramaSyncDisplayRun> findRuns(@Param("providerId") Long providerId,
                                       @Param("domain") DramaSyncDomain domain);

    List<DramaSyncDisplayRunItem> findItems(@Param("runId") String runId,
                                            @Param("domain") DramaSyncDomain domain);

    int deleteItemByTask(@Param("taskDomain") DramaSyncDomain taskDomain,
                         @Param("taskId") Long taskId);

    String findRunIdByTask(@Param("taskDomain") DramaSyncDomain taskDomain,
                           @Param("taskId") Long taskId);
}
