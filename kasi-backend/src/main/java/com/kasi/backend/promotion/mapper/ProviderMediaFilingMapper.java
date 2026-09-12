package com.kasi.backend.promotion.mapper;

import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.SubmissionResolution;
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
    List<ProviderMediaFiling> findByConnectionAndMediaTypeForUpdate(
            @Param("connectionId") Long connectionId, @Param("mediaType") com.kasi.backend.promotion.enums.MediaType mediaType);
    int insert(ProviderMediaFiling entity);
    int switchMethodAndSchedule(@Param("id") Long id, @Param("filingMethod") FilingMethod filingMethod,
                                @Param("status") FilingStatus status, @Param("action") FilingAction action,
                                @Param("nextActionAt") LocalDateTime nextActionAt,
                                @Param("expectedVersion") int expectedVersion,
                                @Param("now") LocalDateTime now);
    int retrySubmission(@Param("id") Long id, @Param("expectedVersion") int expectedVersion,
                        @Param("nextActionAt") LocalDateTime nextActionAt);
    int updateManualStatus(@Param("id") Long id, @Param("expectedStatus") FilingStatus expectedStatus,
                           @Param("status") FilingStatus status, @Param("expectedVersion") int expectedVersion,
                           @Param("operatorId") Long operatorId, @Param("manualAt") LocalDateTime manualAt,
                           @Param("now") LocalDateTime now);
    int resolveUnknownSubmission(@Param("id") Long id, @Param("resolution") SubmissionResolution resolution,
                                 @Param("expectedVersion") int expectedVersion,
                                 @Param("operatorId") Long operatorId,
                                 @Param("manualAt") LocalDateTime manualAt,
                                 @Param("now") LocalDateTime now);
    List<Long> findDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int claimLease(@Param("id") Long id, @Param("owner") String owner,
                   @Param("filingMethod") FilingMethod filingMethod,
                   @Param("expectedAction") FilingAction expectedAction,
                   @Param("taskDataVersion") int taskDataVersion,
                   @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
    int markSubmitAttempt(@Param("id") Long id, @Param("owner") String owner,
                          @Param("filingMethod") FilingMethod filingMethod,
                          @Param("expectedAction") FilingAction expectedAction,
                          @Param("taskDataVersion") int taskDataVersion,
                          @Param("attemptedAt") LocalDateTime attemptedAt);
    int markSubmissionUnknown(@Param("id") Long id, @Param("owner") String owner,
                              @Param("filingMethod") FilingMethod filingMethod,
                              @Param("expectedAction") FilingAction expectedAction,
                              @Param("taskDataVersion") int taskDataVersion,
                              @Param("errorCode") String errorCode,
                              @Param("errorMessage") String errorMessage);
    int completeSubmit(@Param("id") Long id, @Param("owner") String owner,
                       @Param("filingMethod") FilingMethod filingMethod,
                       @Param("expectedAction") FilingAction expectedAction,
                       @Param("taskDataVersion") int taskDataVersion,
                       @Param("submittedAt") LocalDateTime submittedAt,
                       @Param("nextQueryAt") LocalDateTime nextQueryAt);
    int completeQuery(@Param("id") Long id, @Param("owner") String owner,
                      @Param("filingMethod") FilingMethod filingMethod,
                      @Param("expectedAction") FilingAction expectedAction,
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
                    @Param("filingMethod") FilingMethod filingMethod,
                    @Param("expectedAction") FilingAction expectedAction,
                    @Param("taskDataVersion") int taskDataVersion,
                    @Param("status") FilingStatus status,
                    @Param("nextAction") FilingAction nextAction,
                    @Param("nextActionAt") LocalDateTime nextActionAt,
                    @Param("retryCount") int retryCount,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage);
    int deleteByMediaAccountId(@Param("mediaAccountId") Long mediaAccountId);
}
