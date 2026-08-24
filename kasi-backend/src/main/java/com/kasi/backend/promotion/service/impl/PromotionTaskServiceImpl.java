package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.promotion.dto.CreatePromotionTaskDTO;
import com.kasi.backend.promotion.dto.PromotionTaskPageQueryDTO;
import com.kasi.backend.promotion.entity.PromotionTask;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.enums.PromotionTaskStatus;
import com.kasi.backend.promotion.mapper.PromotionTaskMapper;
import com.kasi.backend.promotion.service.PromotionTaskService;
import com.kasi.backend.promotion.vo.PromotionTaskPageVO;
import com.kasi.backend.promotion.vo.PromotionTaskVO;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionTaskServiceImpl implements PromotionTaskService {
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("TIKTOK", "FACEBOOK", "YOUTUBE", "INSTAGRAM");
    private final PromotionTaskMapper taskMapper;
    private final ProviderDramaMapper dramaMapper;
    private final ProviderRuntimeConnectionService runtimeService;

    @Override
    @Transactional(readOnly = true)
    public PromotionTaskPageVO getMine(Long userId, PromotionTaskPageQueryDTO query) {
        int offset = (query.getPage() - 1) * query.getSize();
        List<PromotionTaskVO> list = taskMapper.pageByUser(userId, query.getTaskName(), query.getDramaTitle(),
                        query.getMediaType(), offset, query.getSize()).stream().map(this::toVO).toList();
        return PromotionTaskPageVO.builder().list(list).page(query.getPage()).size(query.getSize())
                .total(taskMapper.countByUser(userId, query.getTaskName(), query.getDramaTitle(), query.getMediaType())).build();
    }

    @Override
    @Transactional
    public PromotionTaskPageVO create(Long userId, CreatePromotionTaskDTO request) {
        ProviderDrama drama = dramaMapper.findById(request.getDramaId());
        if (drama == null || drama.getLocalStatus() != DramaLocalStatus.PUBLISHED || !"1".equals(drama.getRemoteShowStatus())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        ProviderRuntimeConnection runtime = runtimeService.resolve(request.getProviderId(), ProviderCapability.PROMOTION_LINK);
        if (!runtime.connectionId().equals(drama.getConnectionId())) {
            throw new BusinessException(ErrorCode.PROMOTION_LINK_DRAMA_UNAVAILABLE);
        }
        for (String rawType : request.getMediaTypes()) {
            String type = rawType.trim().toUpperCase();
            if (!SUPPORTED_MEDIA_TYPES.contains(type)) throw new BusinessException(ErrorCode.MEDIA_TYPE_UNSUPPORTED);
            if (taskMapper.findByRequestAndMedia(userId, request.getRequestKey(), type) != null) continue;
            PromotionTask task = new PromotionTask();
            task.setUserId(userId); task.setProviderId(request.getProviderId()); task.setConnectionId(runtime.connectionId());
            task.setDramaId(drama.getId()); task.setRequestKey(request.getRequestKey()); task.setTaskName(request.getTaskName().trim());
            task.setMediaType(MediaType.valueOf(type)); task.setTrackingNo(UUID.randomUUID().toString().replace("-", ""));
            task.setStatus(PromotionTaskStatus.PENDING); task.setOrderAmount(BigDecimal.ZERO); task.setAdAmount(BigDecimal.ZERO);
            taskMapper.insert(task);
        }
        return getMine(userId, new PromotionTaskPageQueryDTO());
    }

    private PromotionTaskVO toVO(PromotionTask task) {
        return PromotionTaskVO.builder().id(task.getId()).taskName(task.getTaskName()).mediaType(task.getMediaType())
                .providerName(task.getProviderName()).dramaTitle(task.getDramaTitle()).trackingNo(task.getTrackingNo())
                .externalCode(task.getExternalCode()).directUrl(task.getDirectUrl()).status(task.getStatus())
                .lastErrorMessage(task.getLastErrorMessage()).codeSearchCount(task.getCodeSearchCount())
                .directClickCount(task.getDirectClickCount()).appClickCount(task.getAppClickCount()).leadCount(task.getLeadCount())
                .orderAmount(task.getOrderAmount()).orderCount(task.getOrderCount()).adAmount(task.getAdAmount())
                .createdAt(task.getCreatedAt()).build();
    }
}
