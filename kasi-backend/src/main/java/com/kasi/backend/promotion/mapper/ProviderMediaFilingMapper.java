package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProviderMediaFilingMapper {
    ProviderMediaFiling findById(@Param("id") Long id);
    ProviderMediaFiling findByConnectionAndMedia(@Param("connectionId") Long connectionId,
                                                 @Param("mediaAccountId") Long mediaAccountId);
    List<ProviderMediaFiling> findByMediaAccountId(@Param("mediaAccountId") Long mediaAccountId);
    List<ProviderMediaFiling> findByMediaAccountIds(@Param("mediaAccountIds") List<Long> mediaAccountIds);
    int insert(ProviderMediaFiling entity);
    int enqueue(@Param("id") Long id, @Param("status") FilingStatus status,
                @Param("action") FilingAction action, @Param("taskDataVersion") int taskDataVersion,
                @Param("nextActionAt") LocalDateTime nextActionAt);
    int reschedule(@Param("id") Long id, @Param("status") FilingStatus status,
                   @Param("action") FilingAction action, @Param("expectedVersion") int expectedVersion,
                   @Param("taskDataVersion") int taskDataVersion, @Param("nextActionAt") LocalDateTime nextActionAt);
    List<Long> findDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claimLease(@Param("id") Long id, @Param("owner") String owner,
                   @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    int completeSubmit(@Param("id") Long id, @Param("owner") String owner,
                       @Param("taskDataVersion") int taskDataVersion,
                       @Param("submittedAt") LocalDateTime submittedAt,
                       @Param("nextQueryAt") LocalDateTime nextQueryAt);
    int completeQuery(@Param("id") Long id, @Param("owner") String owner,
                      @Param("taskDataVersion") int taskDataVersion,
                      @Param("status") FilingStatus status,
                      @Param("remoteStatus") String remoteStatus,
                      @Param("externalFilingId") String externalFilingId,
                      @Param("filingTime") LocalDateTime filingTime,
                      @Param("operateTime") LocalDateTime operateTime,
                      @Param("queriedAt") LocalDateTime queriedAt,
                      @Param("nextAction") FilingAction nextAction,
                      @Param("nextActionAt") LocalDateTime nextActionAt);
    int recordRetry(@Param("id") Long id, @Param("owner") String owner,
                    @Param("taskDataVersion") int taskDataVersion,
                    @Param("status") FilingStatus status,
                    @Param("nextAction") FilingAction nextAction,
                    @Param("nextActionAt") LocalDateTime nextActionAt,
                    @Param("retryCount") int retryCount,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage);
    int updateManualStatus(@Param("id") Long id, @Param("status") FilingStatus status,
                           @Param("operateBy") Long operateBy,
                           @Param("operateTime") LocalDateTime operateTime);
    int stopPendingTasksByConnectionId(@Param("connectionId") Long connectionId);
}
