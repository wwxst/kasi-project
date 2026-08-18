package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.AdminMediaAccountPageQueryDTO;
import com.kasi.backend.promotion.dto.AdminUpdateMediaAccountDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
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
        List<AdminMediaAccountListItemVO> all = mediaMapper.findAll().stream()
                .map(this::toListItem).filter(item -> matches(item, query)).toList();
        long total = all.size();
        int from = Math.min((query.getPage() - 1) * query.getSize(), all.size());
        int to = Math.min(from + query.getSize(), all.size());
        return AdminMediaAccountPageVO.builder().list(all.subList(from, to)).page(query.getPage())
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

    private AdminMediaAccountListItemVO toListItem(PromotionMediaAccount account) {
        PromotionUser user = userMapper.findById(account.getUserId());
        List<ProviderMediaFiling> filings = filingMapper.findByMediaAccountId(account.getId());
        Long providerId = null;
        if (filings != null && !filings.isEmpty()) {
            ShortDramaConnection connection = connectionMapper.findById(filings.get(0).getConnectionId());
            providerId = connection == null ? null : connection.getProviderId();
        }
        return AdminMediaAccountListItemVO.builder().id(account.getId())
                .userNo(user == null ? null : user.getUserNo()).nickname(user == null ? null : user.getNickname())
                .realName(user == null ? null : user.getRealName()).mediaType(account.getMediaType())
                .externalAccountId(account.getExternalAccountId()).accountName(account.getAccountName())
                .providerId(providerId).status(account.getStatus())
                .filingStatus(filings == null || filings.isEmpty() ? null : filings.get(0).getStatus())
                .updatedAt(account.getUpdatedAt()).build();
    }

    private boolean matches(AdminMediaAccountListItemVO item, AdminMediaAccountPageQueryDTO query) {
        return (query.getUserNo() == null || query.getUserNo().isBlank() || query.getUserNo().equals(item.getUserNo()))
                && (query.getMediaType() == null || query.getMediaType() == item.getMediaType())
                && (query.getAccountStatus() == null || query.getAccountStatus().equals(item.getStatus()))
                && (query.getProviderId() == null || query.getProviderId().equals(item.getProviderId()))
                && (query.getFilingStatus() == null || query.getFilingStatus() == item.getFilingStatus());
    }
}
