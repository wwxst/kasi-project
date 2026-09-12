package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.dto.UpdateManualFilingStatusDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.SubmissionResolution;
import com.kasi.backend.promotion.export.XlsxExportSupport;
import com.kasi.backend.promotion.mapper.MediaAccountFilingExportMapper;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.MediaAccountAdminService;
import com.kasi.backend.promotion.service.MediaAccountService;
import com.kasi.backend.promotion.vo.AdminMediaAccountVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountListItemVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.AdminMediaFilingVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;
import com.kasi.backend.promotion.vo.MediaAccountFilingExportRow;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MediaAccountAdminServiceImpl implements MediaAccountAdminService {
    private static final List<String> EXPORT_HEADERS = List.of(
            "创建时间", "昵称", "姓名", "电话", "微信号", "媒体平台",
            "账号 ID", "账号名称", "账号链接", "报备状态", "短剧平台");
    private final PromotionMediaAccountMapper mediaMapper;
    private final ProviderMediaFilingMapper filingMapper;
    private final PromotionUserMapper userMapper;
    private final MediaAccountService mediaAccountService;
    private final ShortDramaConnectionMapper connectionMapper;
    private final ShortDramaProviderMapper providerMapper;
    private final MediaAccountFilingExportMapper exportMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminMediaAccountPageVO getPage(AdminMediaAccountPageQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();
        long total = mediaMapper.countAdminPage(query);
        List<AdminMediaAccountListItemVO> list = mediaMapper.findAdminPage(query, offset, query.getSize()).stream()
                .map(account -> toListItem(account, query)).toList();
        return AdminMediaAccountPageVO.builder().list(list).page(query.getPage())
                .size(query.getSize()).total(total).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMediaAccountDetailVO getById(Long id) {
        PromotionMediaAccount account = mediaMapper.findById(id);
        if (account == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        PromotionUser user = userMapper.findById(account.getUserId());
        if (user == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        return AdminMediaAccountDetailVO.builder().id(id).userNo(user.getUserNo()).nickname(user.getNickname())
                .realName(user.getRealName()).mediaAccount(toAdminAccount(account)).build();
    }

    @Override
    @Transactional
    public MediaFilingVO retry(Long id, Long providerId) {
        return mediaAccountService.retryFailedSubmission(id, providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportXlsx(AdminMediaAccountPageQueryDTO query) {
        List<MediaAccountFilingExportRow> rows = exportMapper.findForExport(query);
        return XlsxExportSupport.write("账号报白", EXPORT_HEADERS, rows, (row, value, dateStyle) -> {
            XlsxExportSupport.date(row, 0, value.getCreatedAt(), dateStyle);
            XlsxExportSupport.text(row, 1, value.getNickname());
            XlsxExportSupport.text(row, 2, value.getRealName());
            XlsxExportSupport.text(row, 3, value.getMobile());
            XlsxExportSupport.text(row, 4, value.getWechatId());
            XlsxExportSupport.text(row, 5, mediaTypeLabel(value));
            XlsxExportSupport.text(row, 6, value.getExternalAccountId());
            XlsxExportSupport.text(row, 7, value.getAccountName());
            XlsxExportSupport.text(row, 8, value.getAccountLink());
            XlsxExportSupport.text(row, 9, filingStatusLabel(value));
            XlsxExportSupport.text(row, 10, value.getProviderName());
        });
    }

    @Override
    @Transactional
    public MediaFilingVO updateManualStatus(Long operatorId, Long id, Long providerId,
                                            UpdateManualFilingStatusDTO request) {
        Set<FilingStatus> targets = Set.of(FilingStatus.APPROVED, FilingStatus.REJECTED);
        if (request == null || !targets.contains(request.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        ProviderMediaFiling filing = requireFiling(id, providerId);
        LocalDateTime now = LocalDateTime.now();
        if (filing.getFilingMethod() != FilingMethod.MANUAL
                || hasActiveLease(filing, now)
                || !isAllowedManualTransition(filing.getStatus(), request.getStatus())) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_MANUAL_UPDATE_NOT_ALLOWED);
        }
        int affected = filingMapper.updateManualStatus(filing.getId(), filing.getStatus(), request.getStatus(),
                filing.getTaskDataVersion(), operatorId, now, now);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_CONCURRENT_UPDATE);
        }
        return toFilingVO(filingMapper.findById(filing.getId()), providerId);
    }

    @Override
    @Transactional
    public MediaFilingVO resolveSubmission(Long operatorId, Long id, Long providerId,
                                           SubmissionResolution resolution) {
        if (resolution == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        ProviderMediaFiling filing = requireFiling(id, providerId);
        LocalDateTime now = LocalDateTime.now();
        if (filing.getFilingMethod() != FilingMethod.API
                || filing.getStatus() != FilingStatus.SUBMIT_FAILED
                || !"SUBMIT_OUTCOME_UNKNOWN".equals(filing.getLastErrorCode())
                || filing.getSubmittedDataVersion() != null
                || hasActiveLease(filing, now)) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_RESOLUTION_NOT_ALLOWED);
        }
        int affected = filingMapper.resolveUnknownSubmission(filing.getId(), resolution,
                filing.getTaskDataVersion(), operatorId, now, now);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_CONCURRENT_UPDATE);
        }
        return toFilingVO(filingMapper.findById(filing.getId()), providerId);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PromotionMediaAccount account = mediaMapper.findByIdForUpdate(id);
        if (account == null) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        }
        filingMapper.deleteByMediaAccountId(id);
        if (mediaMapper.deleteById(id) != 1) {
            throw new IllegalStateException("媒体账号删除未生效");
        }
    }

    private ProviderMediaFiling requireFiling(Long mediaAccountId, Long providerId) {
        PromotionMediaAccount account = mediaMapper.findById(mediaAccountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        }
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND);
        }
        ProviderMediaFiling filing = filingMapper.findByConnectionAndMedia(connection.getId(), mediaAccountId);
        if (filing == null) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND);
        }
        return filing;
    }

    private boolean hasActiveLease(ProviderMediaFiling filing, LocalDateTime now) {
        return filing.getLeaseUntil() != null && filing.getLeaseUntil().isAfter(now);
    }

    private boolean isAllowedManualTransition(FilingStatus source, FilingStatus target) {
        return switch (source) {
            case NOT_SUBMITTED, SUBMIT_FAILED -> target == FilingStatus.APPROVED
                    || target == FilingStatus.REJECTED;
            case PENDING -> target == FilingStatus.APPROVED || target == FilingStatus.REJECTED;
            case APPROVED -> target == FilingStatus.REJECTED;
            case REJECTED -> target == FilingStatus.APPROVED;
        };
    }

    private String mediaTypeLabel(MediaAccountFilingExportRow row) {
        if (row.getMediaType() == null) return "";
        return switch (row.getMediaType()) {
            case FACEBOOK -> "Facebook";
            case TIKTOK -> "TikTok";
            case YOUTUBE -> "YouTube";
            case INSTAGRAM -> "Instagram";
        };
    }

    private String filingStatusLabel(MediaAccountFilingExportRow row) {
        if (row.getFilingStatus() == null) return "待提交";
        return switch (row.getFilingStatus()) {
            case NOT_SUBMITTED -> "待提交";
            case PENDING -> "审核中";
            case APPROVED -> "已加白";
            case REJECTED -> "未通过";
            case SUBMIT_FAILED -> "提交失败";
        };
    }

    private MediaFilingVO toFilingVO(ProviderMediaFiling filing, Long providerId) {
        if (filing == null) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND);
        }
        return MediaFilingVO.builder().providerId(providerId).filingMethod(filing.getFilingMethod())
                .status(filing.getStatus())
                .remoteStatus(filing.getRemoteStatus()).externalFilingId(filing.getExternalFilingId())
                .filingTime(filing.getFilingTime()).operateTime(filing.getOperateTime())
                .lastSubmittedAt(filing.getLastSubmittedAt()).lastQueriedAt(filing.getLastQueriedAt())
                .nextActionAt(filing.getNextActionAt()).lastErrorCode(filing.getLastErrorCode())
                .lastErrorMessage(filing.getLastErrorMessage()).build();
    }

    private AdminMediaAccountListItemVO toListItem(PromotionMediaAccount account,
                                                    AdminMediaAccountPageQueryDTO query) {
        PromotionUser user = userMapper.findById(account.getUserId());
        List<ProviderMediaFiling> filings = filingMapper.findByMediaAccountId(account.getId());
        ProviderMediaFiling selectedFiling = selectFiling(filings, query);
        ShortDramaConnection connection = selectedFiling == null
                ? null : connectionMapper.findById(selectedFiling.getConnectionId());
        Long providerId = connection == null ? null : connection.getProviderId();
        return AdminMediaAccountListItemVO.builder().id(account.getId())
                .userNo(user == null ? null : user.getUserNo()).nickname(user == null ? null : user.getNickname())
                .realName(user == null ? null : user.getRealName()).mediaType(account.getMediaType())
                .externalAccountId(account.getExternalAccountId()).accountName(account.getAccountName())
                .providerId(providerId).status(account.getStatus())
                .filingMethod(selectedFiling == null ? null : selectedFiling.getFilingMethod())
                .filingStatus(selectedFiling == null ? null : selectedFiling.getStatus())
                .filingRemoteStatus(selectedFiling == null ? null : selectedFiling.getRemoteStatus())
                .filingLastSubmittedAt(selectedFiling == null ? null : selectedFiling.getLastSubmittedAt())
                .filingNextActionAt(selectedFiling == null ? null : selectedFiling.getNextActionAt())
                .filingLastErrorMessage(selectedFiling == null ? null : selectedFiling.getLastErrorMessage())
                .updatedAt(account.getUpdatedAt()).build();
    }

    private ProviderMediaFiling selectFiling(List<ProviderMediaFiling> filings,
                                              AdminMediaAccountPageQueryDTO query) {
        if (filings == null || filings.isEmpty()) {
            return null;
        }
        return filings.stream().filter(filing -> matches(filing, query)).findFirst().orElse(filings.get(0));
    }

    private boolean matches(ProviderMediaFiling filing, AdminMediaAccountPageQueryDTO query) {
        if (query.getFilingMethod() != null && filing.getFilingMethod() != query.getFilingMethod()) {
            return false;
        }
        if (query.getFilingStatus() != null && !query.getFilingStatus().isBlank()
                && filing.getStatus() != FilingStatus.valueOf(query.getFilingStatus())) {
            return false;
        }
        if (query.getProviderId() == null) {
            return true;
        }
        ShortDramaConnection connection = connectionMapper.findById(filing.getConnectionId());
        return connection != null && query.getProviderId().equals(connection.getProviderId());
    }

    private AdminMediaAccountVO toAdminAccount(PromotionMediaAccount account) {
        List<ProviderMediaFiling> filings = filingMapper.findByMediaAccountId(account.getId());
        List<AdminMediaFilingVO> filingVOs = filings == null ? List.of()
                : filings.stream().map(this::toAdminFilingVO).toList();
        return AdminMediaAccountVO.builder().id(account.getId()).mediaType(account.getMediaType())
                .externalAccountId(account.getExternalAccountId()).accountName(account.getAccountName())
                .accountLink(account.getAccountLink()).status(account.getStatus())
                .createdAt(account.getCreatedAt()).updatedAt(account.getUpdatedAt()).filings(filingVOs).build();
    }

    private AdminMediaFilingVO toAdminFilingVO(ProviderMediaFiling filing) {
        ShortDramaConnection connection = connectionMapper.findById(filing.getConnectionId());
        Long providerId = connection == null ? null : connection.getProviderId();
        ShortDramaProvider provider = providerId == null ? null : providerMapper.findById(providerId);
        LocalDateTime now = LocalDateTime.now();
        boolean retryAllowed = filing.getFilingMethod() == FilingMethod.API
                && filing.getStatus() == FilingStatus.SUBMIT_FAILED
                && filing.getLastSubmittedAt() == null
                && filing.getNextAction() == FilingAction.NONE
                && "SUBMIT_CONFIRMED_NOT_RECEIVED".equals(filing.getLastErrorCode());
        boolean resolutionAllowed = filing.getFilingMethod() == FilingMethod.API
                && filing.getStatus() == FilingStatus.SUBMIT_FAILED
                && "SUBMIT_OUTCOME_UNKNOWN".equals(filing.getLastErrorCode())
                && filing.getSubmittedDataVersion() == null
                && !hasActiveLease(filing, now);
        return AdminMediaFilingVO.builder().providerId(providerId)
                .providerName(provider == null ? null : provider.getProviderName())
                .filingMethod(filing.getFilingMethod()).status(filing.getStatus())
                .remoteStatus(filing.getRemoteStatus()).externalFilingId(filing.getExternalFilingId())
                .filingTime(filing.getFilingTime()).operateTime(filing.getOperateTime())
                .lastSubmittedAt(filing.getLastSubmittedAt()).lastQueriedAt(filing.getLastQueriedAt())
                .nextActionAt(filing.getNextActionAt()).manualUpdatedBy(filing.getManualUpdatedBy())
                .manualUpdatedAt(filing.getManualUpdatedAt()).lastErrorCode(filing.getLastErrorCode())
                .lastErrorMessage(filing.getLastErrorMessage()).retryAllowed(retryAllowed)
                .submissionResolutionAllowed(resolutionAllowed).build();
    }

}
