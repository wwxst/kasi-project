package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.dto.AdminUpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountStatusDTO;
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
import com.kasi.backend.provider.enums.FilingMode;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
            if (filingMode(runtime.connectionId()) == FilingMode.MANUAL) {
                filing.setNextAction(FilingAction.NONE);
                filing.setNextActionAt(null);
            } else {
                filing.setNextAction(FilingAction.SUBMIT);
                filing.setNextActionAt(LocalDateTime.now());
            }
            filingMapper.insert(filing);
            if (filing.getNextAction() == FilingAction.SUBMIT) {
                registerImmediateSubmit(filing.getId());
            }
        }
        return getMineById(userId, account.getId());
    }

    @Override
    @Transactional
    public com.kasi.backend.promotion.vo.MediaAccountDetailVO update(Long userId, Long id, UpdateMediaAccountDTO request) {
        PromotionMediaAccount account = requireOwnedForUpdate(id, userId);
        if (!Integer.valueOf(1).equals(account.getStatus())) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DISABLED);
        applyDetailsUpdate(account, request.getMediaType(), request.getExternalAccountId(),
                request.getAccountName(), request.getAccountLink());
        return getMineById(userId, id);
    }

    @Override
    @Transactional
    public com.kasi.backend.promotion.vo.MediaAccountDetailVO updateByAdmin(Long id,
                                                                              AdminUpdateMediaAccountDTO request) {
        PromotionMediaAccount account = mediaMapper.findByIdForUpdate(id);
        if (account == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        applyDetailsUpdate(account, request.getMediaType(), request.getExternalAccountId(),
                request.getAccountName(), request.getAccountLink());
        if (mediaMapper.updateStatus(id, request.getStatus()) != 1) {
            throw new IllegalStateException("媒体账号状态更新未生效");
        }
        return toDetailVO(mediaMapper.findById(id));
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, Long id, UpdateMediaAccountStatusDTO request) {
        PromotionMediaAccount account = requireOwnedForUpdate(id, userId);
        if (mediaMapper.updateStatus(account.getId(), request.getStatus()) != 1) {
            throw new IllegalStateException("媒体账号状态更新未生效");
        }
    }

    @Override
    @Transactional
    public com.kasi.backend.promotion.vo.MediaFilingVO submitOrRetry(Long userId, Long id, Long providerId) {
        PromotionMediaAccount account = requireOwnedForUpdate(id, userId);
        if (!Integer.valueOf(1).equals(account.getStatus())) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DISABLED);
        ProviderRuntimeConnection runtime = resolve(providerId, account.getMediaType());
        FilingMode mode = filingMode(runtime.connectionId());
        ProviderMediaFiling filing = filingMapper.findByConnectionAndMedia(runtime.connectionId(), id);
        if (filing == null) {
            if (mode == FilingMode.MANUAL) {
                throw new BusinessException(ErrorCode.MEDIA_FILING_MANUAL_ONLY);
            }
            filing = new ProviderMediaFiling();
            filing.setConnectionId(runtime.connectionId());
            filing.setMediaAccountId(id);
            filing.setStatus(FilingStatus.PENDING);
            filing.setTaskDataVersion(account.getDataVersion());
            filing.setNextAction(FilingAction.SUBMIT);
            filing.setNextActionAt(LocalDateTime.now());
            filingMapper.insert(filing);
            registerImmediateSubmit(filing.getId());
        } else if (filing.getStatus() == FilingStatus.APPROVED) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_APPROVED);
        } else if (mode == FilingMode.MANUAL) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_MANUAL_ONLY);
        } else if (filing.getStatus() == FilingStatus.FAILED || filing.getNextAction() == FilingAction.NONE) {
            filingMapper.reschedule(filing.getId(), FilingStatus.PENDING, FilingAction.SUBMIT,
                    filing.getTaskDataVersion(), account.getDataVersion(), LocalDateTime.now());
            filing = filingMapper.findById(filing.getId());
            registerImmediateSubmit(filing.getId());
        }
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

    private void resolveExistingProviderMedia(MediaType mediaType, List<ProviderMediaFiling> filings) {
        if (connectionMapper == null || providerMapper == null) {
            return;
        }
        for (ProviderMediaFiling filing : filings) {
            ShortDramaConnection connection = connectionMapper.findById(filing.getConnectionId());
            if (connection == null) continue;
            resolve(connection.getProviderId(), mediaType);
        }
    }

    private void applyDetailsUpdate(PromotionMediaAccount account, MediaType mediaType,
                                    String externalAccountId, String accountName, String accountLink) {
        List<ProviderMediaFiling> filings = filings(account.getId());
        String externalId = requiredTrim(externalAccountId);
        boolean identityChanged = mediaType != account.getMediaType()
                || !externalId.equals(account.getExternalAccountId());
        if (identityChanged && filings.stream().anyMatch(f -> f.getStatus() == FilingStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_IDENTITY_LOCKED);
        }
        if (identityChanged) {
            PromotionMediaAccount duplicate = mediaMapper.findByIdentity(mediaType, externalId);
            if (duplicate != null && !Objects.equals(duplicate.getId(), account.getId())) {
                throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_DUPLICATE);
            }
            resolveExistingProviderMedia(mediaType, filings);
        }
        boolean detailsChanged = identityChanged
                || !Objects.equals(trimToNull(accountName), account.getAccountName())
                || !Objects.equals(trimToNull(accountLink), account.getAccountLink());
        if (!detailsChanged) return;
        int previousVersion = account.getDataVersion();
        account.setMediaType(mediaType);
        account.setExternalAccountId(externalId);
        account.setAccountName(trimToNull(accountName));
        account.setAccountLink(trimToNull(accountLink));
        account.setDataVersion(previousVersion + 1);
        mediaMapper.updateDetails(account);
        for (ProviderMediaFiling filing : filings) {
            FilingStatus nextStatus = filing.getStatus() == FilingStatus.APPROVED && !identityChanged
                    ? FilingStatus.APPROVED : FilingStatus.PENDING;
            boolean manual = filingMode(filing.getConnectionId()) == FilingMode.MANUAL;
            filingMapper.reschedule(filing.getId(), nextStatus,
                    manual ? FilingAction.NONE : FilingAction.SUBMIT,
                    filing.getTaskDataVersion(), account.getDataVersion(),
                    manual ? null : LocalDateTime.now());
            if (!manual && nextStatus != FilingStatus.APPROVED) {
                registerImmediateSubmit(filing.getId());
            }
        }
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

    private PromotionMediaAccount requireOwnedForUpdate(Long id, Long userId) {
        PromotionMediaAccount account = mediaMapper.findByIdForUpdate(id);
        if (account == null || !Objects.equals(account.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private List<ProviderMediaFiling> filings(Long id) {
        List<ProviderMediaFiling> result = filingMapper.findByMediaAccountId(id);
        return result == null ? Collections.emptyList() : result;
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
                .operateTime(filing.getOperateTime()).operateBy(filing.getOperateBy())
                .lastSubmittedAt(filing.getLastSubmittedAt())
                .lastQueriedAt(filing.getLastQueriedAt()).nextActionAt(filing.getNextActionAt())
                .lastErrorCode(filing.getLastErrorCode())
                .lastErrorMessage(filing.getLastErrorMessage()).build();
    }

    private String requiredTrim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }

    private FilingMode filingMode(Long connectionId) {
        if (connectionMapper == null) return FilingMode.API;
        ShortDramaConnection connection = connectionMapper.findById(connectionId);
        if (connection == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        }
        if (connection.getFilingMode() == null) {
            throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_INVALID);
        }
        return connection.getFilingMode();
    }
}
