package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.MediaAccountService;
import com.kasi.backend.promotion.service.MediaFilingTaskService;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MediaAccountServiceImpl implements MediaAccountService {
    private final PromotionMediaAccountMapper mediaMapper;
    private final ProviderMediaFilingMapper filingMapper;
    private final com.kasi.backend.provider.service.ProviderRuntimeConnectionService runtimeService;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ShortDramaProviderMapper providerMapper;
    private final MediaFilingTaskService filingTaskService;
    private static final Logger log = LoggerFactory.getLogger(MediaAccountServiceImpl.class);

    public MediaAccountServiceImpl(PromotionMediaAccountMapper mediaMapper,
                                   ProviderMediaFilingMapper filingMapper,
                                   com.kasi.backend.provider.service.ProviderRuntimeConnectionService runtimeService) {
        this(mediaMapper, filingMapper, runtimeService, null, null, null);
    }

    public MediaAccountServiceImpl(PromotionMediaAccountMapper mediaMapper,
                                   ProviderMediaFilingMapper filingMapper,
                                   com.kasi.backend.provider.service.ProviderRuntimeConnectionService runtimeService,
                                   ShortDramaConnectionMapper connectionMapper,
                                   ShortDramaProviderMapper providerMapper) {
        this(mediaMapper, filingMapper, runtimeService, connectionMapper, providerMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MediaAccountServiceImpl(PromotionMediaAccountMapper mediaMapper,
                                   ProviderMediaFilingMapper filingMapper,
                                   com.kasi.backend.provider.service.ProviderRuntimeConnectionService runtimeService,
                                   ShortDramaConnectionMapper connectionMapper,
                                   ShortDramaProviderMapper providerMapper,
                                   MediaFilingTaskService filingTaskService) {
        this.mediaMapper = mediaMapper;
        this.filingMapper = filingMapper;
        this.runtimeService = runtimeService;
        this.connectionMapper = connectionMapper;
        this.providerMapper = providerMapper;
        this.filingTaskService = filingTaskService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.kasi.backend.promotion.vo.MediaAccountVO> getMine(Long userId) {
        return mediaMapper.findByUserId(userId).stream().map(this::toListVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public com.kasi.backend.promotion.vo.MediaAccountDetailVO getMineById(Long userId, Long id) {
        return toDetailVO(requireOwned(id, userId));
    }

    @Override
    @Transactional
    public com.kasi.backend.promotion.vo.MediaAccountDetailVO create(Long userId, CreateMediaAccountDTO request) {
        String externalId = requiredTrim(request.getExternalAccountId());
        List<ProviderRuntimeConnection> runtimes = runtimeService.resolveAll(ProviderCapability.ACCOUNT_FILING).stream()
                .filter(runtime -> runtime.adapter() instanceof AccountFilingProviderAdapter filingAdapter
                        && filingAdapter.supportedMediaTypes().contains(request.getMediaType()))
                .toList();
        if (runtimes.isEmpty()) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        }
        if (mediaMapper.findByIdentity(request.getMediaType(), externalId) != null) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DUPLICATE);
        }
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setUserId(userId);
        account.setMediaType(request.getMediaType());
        account.setExternalAccountId(externalId);
        account.setAccountName(trimToNull(request.getAccountName()));
        account.setAccountLink(trimToNull(request.getAccountLink()));
        account.setStatus(1);
        account.setDataVersion(1);
        try {
            mediaMapper.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DUPLICATE);
        }
        for (ProviderRuntimeConnection runtime : runtimes) {
            ProviderMediaFiling filing = new ProviderMediaFiling();
            filing.setConnectionId(runtime.connectionId());
            filing.setMediaAccountId(account.getId());
            filing.setStatus(FilingStatus.PENDING);
            filing.setTaskDataVersion(1);
            filing.setNextAction(FilingAction.SUBMIT);
            filing.setNextActionAt(LocalDateTime.now());
            filingMapper.insert(filing);
            registerImmediateSubmit(filing.getId());
        }
        return getMineById(userId, account.getId());
    }

    @Override
    @Transactional
    public com.kasi.backend.promotion.vo.MediaFilingVO retryFailedSubmission(Long id, Long providerId) {
        PromotionMediaAccount account = mediaMapper.findByIdForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        }
        ProviderRuntimeConnection runtime = resolve(providerId, account.getMediaType());
        ProviderMediaFiling filing = filingMapper.findByConnectionAndMedia(runtime.connectionId(), id);
        if (filing == null
                || filing.getLastSubmittedAt() != null
                || filing.getNextAction() != FilingAction.NONE
                || !"REMOTE_TRANSIENT".equals(filing.getLastErrorCode())) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_RETRY_NOT_ALLOWED);
        }
        int affected = filingMapper.reschedule(filing.getId(), FilingStatus.PENDING, FilingAction.SUBMIT,
                filing.getTaskDataVersion(), account.getDataVersion(), LocalDateTime.now());
        if (affected != 1) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_RETRY_NOT_ALLOWED);
        }
        filing = filingMapper.findById(filing.getId());
        if (filing == null) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND);
        }
        registerImmediateSubmit(filing.getId());
        return toFilingVO(filing);
    }

    private ProviderRuntimeConnection resolve(Long providerId, MediaType mediaType) {
        ProviderRuntimeConnection runtime = runtimeService.resolve(providerId, ProviderCapability.ACCOUNT_FILING);
        if (!(runtime.adapter() instanceof AccountFilingProviderAdapter filingAdapter)
                || !filingAdapter.supportedMediaTypes().contains(mediaType)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        return runtime;
    }

    private void registerImmediateSubmit(Long filingId) {
        if (filingTaskService == null) {
            return;
        }
        Runnable submit = () -> {
            try {
                filingTaskService.submitNow(filingId);
            } catch (RuntimeException exception) {
                log.warn("Immediate media filing submission failed for filing {}", filingId, exception);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit.run();
            }
        });
    }

    private PromotionMediaAccount requireOwned(Long id, Long userId) {
        PromotionMediaAccount account = mediaMapper.findOwnedById(id, userId);
        if (account == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        return account;
    }

    private List<ProviderMediaFiling> filings(Long id) {
        List<ProviderMediaFiling> result = filingMapper.findByMediaAccountId(id);
        return result == null ? List.of() : result;
    }

    private com.kasi.backend.promotion.vo.MediaAccountVO toListVO(PromotionMediaAccount account) {
        return com.kasi.backend.promotion.vo.MediaAccountVO.builder().id(account.getId())
                .mediaType(account.getMediaType()).externalAccountId(account.getExternalAccountId())
                .accountName(account.getAccountName()).accountLink(account.getAccountLink()).status(account.getStatus())
                .filings(filings(account.getId()).stream().map(this::toFilingVO).toList()).build();
    }

    private com.kasi.backend.promotion.vo.MediaAccountDetailVO toDetailVO(PromotionMediaAccount account) {
        return com.kasi.backend.promotion.vo.MediaAccountDetailVO.builder().id(account.getId())
                .mediaType(account.getMediaType()).externalAccountId(account.getExternalAccountId())
                .accountName(account.getAccountName()).accountLink(account.getAccountLink()).status(account.getStatus())
                .createdAt(account.getCreatedAt()).updatedAt(account.getUpdatedAt())
                .filings(filings(account.getId()).stream().map(this::toFilingVO).toList()).build();
    }

    private com.kasi.backend.promotion.vo.MediaFilingVO toFilingVO(ProviderMediaFiling filing) {
        Long providerId = null;
        String providerName = null;
        if (connectionMapper != null && providerMapper != null) {
            ShortDramaConnection connection = connectionMapper.findById(filing.getConnectionId());
            if (connection != null) {
                providerId = connection.getProviderId();
                ShortDramaProvider provider = providerMapper.findById(providerId);
                providerName = provider == null ? null : provider.getProviderName();
            }
        }
        return com.kasi.backend.promotion.vo.MediaFilingVO.builder().providerId(providerId).providerName(providerName)
                .status(filing.getStatus()).remoteStatus(filing.getRemoteStatus())
                .externalFilingId(filing.getExternalFilingId()).filingTime(filing.getFilingTime())
                .operateTime(filing.getOperateTime())
                .lastSubmittedAt(filing.getLastSubmittedAt())
                .lastQueriedAt(filing.getLastQueriedAt()).nextActionAt(filing.getNextActionAt())
                .lastErrorCode(filing.getLastErrorCode())
                .lastErrorMessage(filing.getLastErrorMessage()).build();
    }

    private String requiredTrim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
