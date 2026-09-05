package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.dto.AdminUpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaFilingStatusDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.MediaAccountAdminService;
import com.kasi.backend.promotion.service.MediaAccountService;
import com.kasi.backend.promotion.vo.AdminMediaAccountDetailVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountListItemVO;
import com.kasi.backend.promotion.vo.AdminMediaAccountPageVO;
import com.kasi.backend.promotion.vo.MediaFilingVO;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaAccountAdminServiceImpl implements MediaAccountAdminService {
    private final PromotionMediaAccountMapper mediaMapper;
    private final ProviderMediaFilingMapper filingMapper;
    private final PromotionUserMapper userMapper;
    private final MediaAccountService mediaAccountService;
    private final ShortDramaConnectionMapper connectionMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminMediaAccountPageVO getPage(AdminMediaAccountPageQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();
        long total = mediaMapper.countAdminPage(query);
        List<AdminMediaAccountListItemVO> list = mediaMapper.findAdminPage(query, offset, query.getSize()).stream()
                .map(this::toListItem).toList();
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
                .realName(user.getRealName()).mediaAccount(mediaAccountService.getMineById(user.getId(), id)).build();
    }

    @Override
    @Transactional
    public AdminMediaAccountDetailVO update(Long id, AdminUpdateMediaAccountDTO request) {
        mediaAccountService.updateByAdmin(id, request);
        return getById(id);
    }

    @Override
    @Transactional
    public MediaFilingVO retry(Long id, Long providerId) {
        PromotionMediaAccount account = mediaMapper.findById(id);
        if (account == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        return mediaAccountService.submitOrRetry(account.getUserId(), id, providerId);
    }

    @Override
    @Transactional
    public MediaFilingVO updateFilingStatus(Long operatorId, Long mediaAccountId, Long providerId,
                                             UpdateMediaFilingStatusDTO request) {
        PromotionMediaAccount account = mediaMapper.findById(mediaAccountId);
        if (account == null) throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_NOT_FOUND);
        if (request.getStatus() != FilingStatus.APPROVED && request.getStatus() != FilingStatus.FAILED) {
            throw new BusinessException(ErrorCode.MEDIA_FILING_STATUS_INVALID);
        }
        ShortDramaConnection connection = connectionMapper.findByProviderId(providerId);
        if (connection == null) throw new BusinessException(ErrorCode.PROVIDER_CONNECTION_NOT_FOUND);
        ProviderMediaFiling filing = filingMapper.findByConnectionAndMedia(connection.getId(), mediaAccountId);
        if (filing == null) throw new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND);
        int affected = filingMapper.updateManualStatus(filing.getId(), request.getStatus(), operatorId,
                java.time.LocalDateTime.now());
        if (affected != 1) throw new BusinessException(ErrorCode.MEDIA_FILING_STATUS_INVALID);

        return mediaAccountService.getMineById(account.getUserId(), mediaAccountId).getFilings().stream()
                .filter(item -> providerId.equals(item.getProviderId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILING_NOT_FOUND));
    }

    private AdminMediaAccountListItemVO toListItem(PromotionMediaAccount account) {
        PromotionUser user = userMapper.findById(account.getUserId());
        List<ProviderMediaFiling> filings = filingMapper.findByMediaAccountId(account.getId());
        Long providerId = null;
        if (filings != null && !filings.isEmpty()) {
            ShortDramaConnection connection = connectionMapper.findById(filings.get(0).getConnectionId());
            providerId = connection == null ? null : connection.getProviderId();
        }
        ProviderMediaFiling firstFiling = filings == null || filings.isEmpty() ? null : filings.get(0);
        return AdminMediaAccountListItemVO.builder().id(account.getId())
                .userNo(user == null ? null : user.getUserNo()).nickname(user == null ? null : user.getNickname())
                .realName(user == null ? null : user.getRealName()).mediaType(account.getMediaType())
                .externalAccountId(account.getExternalAccountId()).accountName(account.getAccountName())
                .providerId(providerId).status(account.getStatus())
                .filingStatus(firstFiling == null ? null : firstFiling.getStatus())
                .filingRemoteStatus(firstFiling == null ? null : firstFiling.getRemoteStatus())
                .filingLastSubmittedAt(firstFiling == null ? null : firstFiling.getLastSubmittedAt())
                .filingNextActionAt(firstFiling == null ? null : firstFiling.getNextActionAt())
                .filingLastErrorMessage(firstFiling == null ? null : firstFiling.getLastErrorMessage())
                .updatedAt(account.getUpdatedAt()).build();
    }

}
