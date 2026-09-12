package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.MediaFilingMethodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaFilingMethodServiceImpl implements MediaFilingMethodService {
    private final ProviderMediaFilingMapper filingMapper;
    private final tools.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public FilingMethod resolveMethod(String apiFilingMediaTypes, MediaType mediaType) {
        return parse(apiFilingMediaTypes).contains(mediaType) ? FilingMethod.API : FilingMethod.MANUAL;
    }

    @Override
    @Transactional
    public void switchMethods(Long connectionId, Set<MediaType> toApi, Set<MediaType> toManual) {
        if (toApi.isEmpty() && toManual.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProviderMediaFiling> apiFilings = filings(connectionId, toApi);
        List<ProviderMediaFiling> manualFilings = filings(connectionId, toManual);
        List<ProviderMediaFiling> all = java.util.stream.Stream.concat(apiFilings.stream(), manualFilings.stream())
                .toList();
        if (all.stream().anyMatch(filing -> hasActiveLease(filing, now)
                || "SUBMIT_OUTCOME_UNKNOWN".equals(filing.getLastErrorCode())
                || hasUnresolvedSubmitAttempt(filing))) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_METHOD_SWITCH_BLOCKED);
        }
        for (ProviderMediaFiling filing : apiFilings) {
            FilingAction action = switch (filing.getStatus()) {
                case NOT_SUBMITTED -> FilingAction.SUBMIT;
                case PENDING -> FilingAction.QUERY;
                case APPROVED, REJECTED, SUBMIT_FAILED -> FilingAction.NONE;
            };
            switchOne(filing, FilingMethod.API, action, now);
        }
        for (ProviderMediaFiling filing : manualFilings) {
            switchOne(filing, FilingMethod.MANUAL, FilingAction.NONE, now);
        }
    }

    private List<ProviderMediaFiling> filings(Long connectionId, Set<MediaType> mediaTypes) {
        return mediaTypes.stream()
                .sorted()
                .flatMap(mediaType -> filingMapper.findByConnectionAndMediaTypeForUpdate(connectionId, mediaType).stream())
                .toList();
    }

    private void switchOne(ProviderMediaFiling filing, FilingMethod method, FilingAction action,
                           LocalDateTime now) {
        LocalDateTime nextActionAt = action == FilingAction.NONE ? null : now;
        int affected = filingMapper.switchMethodAndSchedule(filing.getId(), method, filing.getStatus(), action,
                nextActionAt, filing.getTaskDataVersion(), now);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_CONCURRENT_UPDATE);
        }
    }

    private boolean hasActiveLease(ProviderMediaFiling filing, LocalDateTime now) {
        return filing.getLeaseUntil() != null && filing.getLeaseUntil().isAfter(now);
    }

    private boolean hasUnresolvedSubmitAttempt(ProviderMediaFiling filing) {
        return filing.getLastSubmitAttemptAt() != null
                && filing.getSubmittedDataVersion() == null
                && filing.getNextAction() == FilingAction.SUBMIT;
    }

    private Set<MediaType> parse(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            MediaType[] mediaTypes = objectMapper.readValue(value, MediaType[].class);
            return mediaTypes.length == 0 ? Set.of() : EnumSet.copyOf(Arrays.asList(mediaTypes));
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException("API报白媒体配置无法解析", exception);
        }
    }
}
