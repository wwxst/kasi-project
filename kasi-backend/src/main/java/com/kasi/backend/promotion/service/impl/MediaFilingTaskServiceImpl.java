package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.promotion.config.MediaFilingProperties;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.MediaFilingTaskService;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.exception.ProviderRemoteRejectedException;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.AccountFilingQuery;
import com.kasi.backend.provider.spi.AccountFilingResult;
import com.kasi.backend.provider.spi.AccountFilingSubmission;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MediaFilingTaskServiceImpl implements MediaFilingTaskService {
    private final ProviderMediaFilingMapper filingMapper;
    private final PromotionMediaAccountMapper mediaMapper;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ProviderRuntimeConnectionService runtimeService;
    private final MediaFilingProperties properties;
    private final Clock clock;
    private final String workerId = UUID.randomUUID().toString();

    public MediaFilingTaskServiceImpl(ProviderMediaFilingMapper filingMapper,
                                      PromotionMediaAccountMapper mediaMapper,
                                      ShortDramaConnectionMapper connectionMapper,
                                      ProviderRuntimeConnectionService runtimeService,
                                      MediaFilingProperties properties, Clock clock) {
        this.filingMapper = filingMapper;
        this.mediaMapper = mediaMapper;
        this.connectionMapper = connectionMapper;
        this.runtimeService = runtimeService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void processDueBatch() {
        LocalDateTime now = now();
        List<Long> dueIds = filingMapper.findDueIds(now, properties.getBatchSize());
        for (Long id : dueIds) {
            if (filingMapper.claimLease(id, workerId, now, now.plus(properties.getLeaseDuration())) == 1) {
                processClaimed(id, now);
            }
        }
    }

    @Override
    public void submitNow(Long filingId) {
        LocalDateTime now = now();
        ProviderMediaFiling filing = filingMapper.findById(filingId);
        if (filing == null || filing.getNextAction() != FilingAction.SUBMIT) {
            return;
        }
        if (filingMapper.claimLease(filingId, workerId, now, now.plus(properties.getLeaseDuration())) == 1) {
            processClaimed(filingId, now);
        }
    }

    private void processClaimed(Long id, LocalDateTime now) {
        ProviderMediaFiling filing = filingMapper.findById(id);
        if (filing == null) return;
        PromotionMediaAccount account = mediaMapper.findById(filing.getMediaAccountId());
        ShortDramaConnection connection = connectionMapper.findById(filing.getConnectionId());
        if (account == null || connection == null || !Integer.valueOf(1).equals(account.getStatus())) {
            recordFinalFailure(filing, now, "LOCAL_INVALID", "报备所需本地配置不可用");
            return;
        }
        try {
            ProviderCapability capability = filing.getNextAction() == FilingAction.SUBMIT
                    ? ProviderCapability.ACCOUNT_FILING : ProviderCapability.FILING_STATUS_QUERY;
            ProviderRuntimeConnection runtime = runtimeService.resolve(connection.getProviderId(), capability);
            if (!(runtime.adapter() instanceof AccountFilingProviderAdapter adapter)) {
                recordFinalFailure(filing, now, "CAPABILITY_UNSUPPORTED", "平台不支持账号报备");
                return;
            }
            if (filing.getNextAction() == FilingAction.SUBMIT) {
                adapter.submitAccountFiling(runtime.secret(), new AccountFilingSubmission(
                        account.getMediaType(), account.getExternalAccountId(), account.getAccountName(), account.getAccountLink()));
                filingMapper.completeSubmit(filing.getId(), workerId, filing.getTaskDataVersion(), now,
                        now.plus(properties.getFirstQueryDelay()));
            } else if (filing.getNextAction() == FilingAction.QUERY) {
                AccountFilingResult result = adapter.queryAccountFiling(runtime.secret(),
                        new AccountFilingQuery(account.getMediaType(), account.getExternalAccountId()));
                FilingAction nextAction = result.status() == FilingStatus.FAILED ? FilingAction.NONE : FilingAction.QUERY;
                Duration delay = result.status() == FilingStatus.APPROVED
                        ? properties.getApprovedQueryInterval() : properties.getPendingQueryInterval();
                filingMapper.completeQuery(filing.getId(), workerId, filing.getTaskDataVersion(), result.status(),
                        result.remoteStatus(), result.externalFilingId(), result.filingTime(), result.operateTime(),
                        now, nextAction, now.plus(delay));
            }
        } catch (ProviderTransientException exception) {
            recordRetry(filing, now, "REMOTE_TRANSIENT", exception.getMessage());
        } catch (ProviderRemoteRejectedException exception) {
            recordFinalFailure(filing, now, "REMOTE_REJECTED", exception.getMessage());
        } catch (RuntimeException exception) {
            recordFinalFailure(filing, now, "TASK_ERROR", safeMessage(exception));
            throw exception;
        }
    }

    private void recordRetry(ProviderMediaFiling filing, LocalDateTime now, String code, String message) {
        int retries = filing.getRetryCount() == null ? 1 : filing.getRetryCount() + 1;
        if (retries >= properties.getMaxPendingRetries() && filing.getStatus() != FilingStatus.APPROVED) {
            recordFinalFailure(filing, now, code, message);
            return;
        }
        FilingStatus status = filing.getStatus() == FilingStatus.APPROVED ? FilingStatus.APPROVED : FilingStatus.PENDING;
        // Report calls are initiated explicitly after a user action; the scheduled worker only queries.
        FilingAction action = filing.getNextAction() == FilingAction.SUBMIT
                ? FilingAction.NONE : filing.getNextAction();
        LocalDateTime nextActionAt = action == FilingAction.NONE ? null : now.plus(retryDelay(retries));
        filingMapper.recordRetry(filing.getId(), workerId, filing.getTaskDataVersion(), status, action,
                nextActionAt, retries, code, message);
    }

    private void recordFinalFailure(ProviderMediaFiling filing, LocalDateTime now, String code, String message) {
        FilingStatus status = filing.getStatus() == FilingStatus.APPROVED ? FilingStatus.APPROVED : FilingStatus.FAILED;
        FilingAction action = filing.getStatus() == FilingStatus.APPROVED ? FilingAction.QUERY : FilingAction.NONE;
        LocalDateTime next = filing.getStatus() == FilingStatus.APPROVED
                ? now.plus(properties.getApprovedQueryInterval()) : now;
        filingMapper.recordRetry(filing.getId(), workerId, filing.getTaskDataVersion(), status, action,
                next, filing.getRetryCount() == null ? 1 : filing.getRetryCount() + 1, code, message);
    }

    private Duration retryDelay(int retryCount) {
        List<Duration> delays = properties.getRetryDelays();
        return delays.get(Math.min(retryCount - 1, delays.size() - 1));
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
