package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectTypeDTO;
import com.kasi.backend.promotion.entity.PromotionProjectType;
import com.kasi.backend.promotion.mapper.PromotionProjectMapper;
import com.kasi.backend.promotion.mapper.PromotionProjectTypeMapper;
import com.kasi.backend.promotion.service.PromotionProjectTypeService;
import com.kasi.backend.promotion.vo.PromotionProjectTypeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PromotionProjectTypeServiceImpl implements PromotionProjectTypeService {
    private final PromotionProjectTypeMapper projectTypeMapper;
    private final PromotionProjectMapper projectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PromotionProjectTypeVO> list() {
        return projectTypeMapper.findAll().stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public PromotionProjectTypeVO create(UpsertPromotionProjectTypeDTO request) {
        normalize(request);
        requireUniqueCode(request.getCode(), null);
        PromotionProjectType projectType = new PromotionProjectType();
        apply(projectType, request);
        try {
            projectTypeMapper.insert(projectType);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_CODE_EXISTS);
        }
        return toVO(projectType);
    }

    @Override
    @Transactional
    public PromotionProjectTypeVO update(Long id, UpsertPromotionProjectTypeDTO request) {
        normalize(request);
        PromotionProjectType projectType = requireTypeForUpdate(id);
        requireUniqueCode(request.getCode(), id);
        apply(projectType, request);
        try {
            projectTypeMapper.update(projectType);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_CODE_EXISTS);
        }
        return toVO(projectType);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireTypeForUpdate(id);
        if (!projectMapper.findIdsByProjectTypeIdForUpdate(id).isEmpty()) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_IN_USE);
        }
        if (projectTypeMapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_NOT_FOUND);
        }
    }

    private void normalize(UpsertPromotionProjectTypeDTO request) {
        if (request == null || request.getCode() == null || request.getName() == null
                || request.getStatus() == null || request.getSortOrder() == null
                || request.getSortOrder() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        request.setCode(request.getCode().trim().toUpperCase(Locale.ROOT));
        request.setName(request.getName().trim());
        if (!request.getCode().matches("[A-Z][A-Z0-9_]{0,31}")
                || request.getName().isEmpty() || request.getName().length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void requireUniqueCode(String code, Long currentId) {
        PromotionProjectType existing = projectTypeMapper.findByCode(code);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_CODE_EXISTS);
        }
    }

    private PromotionProjectType requireTypeForUpdate(Long id) {
        PromotionProjectType projectType = projectTypeMapper.findByIdForUpdate(id);
        if (projectType == null) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_TYPE_NOT_FOUND);
        }
        return projectType;
    }

    private void apply(PromotionProjectType projectType, UpsertPromotionProjectTypeDTO request) {
        projectType.setCode(request.getCode());
        projectType.setName(request.getName());
        projectType.setStatus(request.getStatus());
        projectType.setSortOrder(request.getSortOrder());
    }

    private PromotionProjectTypeVO toVO(PromotionProjectType projectType) {
        return PromotionProjectTypeVO.builder()
                .id(projectType.getId())
                .code(projectType.getCode())
                .name(projectType.getName())
                .status(projectType.getStatus())
                .sortOrder(projectType.getSortOrder())
                .createdAt(projectType.getCreatedAt())
                .updatedAt(projectType.getUpdatedAt())
                .build();
    }
}
