package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.PromotionProjectPageQueryDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectDTO;
import com.kasi.backend.promotion.entity.PromotionProject;
import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import com.kasi.backend.promotion.mapper.PromotionProjectMapper;
import com.kasi.backend.promotion.service.PromotionProjectCoverStorageService;
import com.kasi.backend.promotion.service.PromotionProjectService;
import com.kasi.backend.promotion.vo.PromotionProjectPageVO;
import com.kasi.backend.promotion.vo.PromotionProjectCardVO;
import com.kasi.backend.promotion.vo.PromotionProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionProjectServiceImpl implements PromotionProjectService {
    private final PromotionProjectMapper projectMapper;
    private final PromotionProjectCoverStorageService coverStorageService;

    @Override
    @Transactional(readOnly = true)
    public PromotionProjectPageVO getPage(PromotionProjectPageQueryDTO query) {
        return PromotionProjectPageVO.builder()
                .list(projectMapper.findPage((query.getPage() - 1) * query.getSize(), query.getSize())
                        .stream().map(this::toVO).toList())
                .page(query.getPage())
                .size(query.getSize())
                .total(projectMapper.countAll())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionProjectVO getById(Long id) {
        return toVO(requireProject(id));
    }

    @Override
    @Transactional
    public PromotionProjectVO create(UpsertPromotionProjectDTO request, MultipartFile coverFile) {
        validate(request);
        String newCover = coverStorageService.store(coverFile);
        PromotionProject project = new PromotionProject();
        apply(project, request);
        project.setCoverImageUrl(newCover);
        try {
            projectMapper.insert(project);
            registerCoverCompletion(newCover, null);
            return toVO(project);
        } catch (RuntimeException exception) {
            coverStorageService.deleteIfLocal(newCover);
            throw exception;
        }
    }

    @Override
    @Transactional
    public PromotionProjectVO update(Long id, UpsertPromotionProjectDTO request, MultipartFile coverFile) {
        validate(request);
        PromotionProject project = requireProject(id);
        String previousCover = project.getCoverImageUrl();
        String newCover = null;
        if (coverFile != null && !coverFile.isEmpty()) {
            newCover = coverStorageService.store(coverFile);
            project.setCoverImageUrl(newCover);
        }
        apply(project, request);
        try {
            projectMapper.update(project);
            if (newCover != null) {
                registerCoverCompletion(newCover, previousCover);
            }
            return toVO(project);
        } catch (RuntimeException exception) {
            if (newCover != null) {
                coverStorageService.deleteIfLocal(newCover);
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PromotionProject project = requireProject(id);
        if (projectMapper.deleteById(id) != 1) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_NOT_FOUND);
        }
        registerCoverCompletion(null, project.getCoverImageUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionProjectCardVO> listEnabled() {
        return projectMapper.findEnabled().stream().map(this::toCardVO).toList();
    }

    private void validate(UpsertPromotionProjectDTO request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()
                || request.getName().trim().length() > 128
                || request.getSortOrder() == null || request.getSortOrder() < 0
                || request.getStatus() == null || !isHttps(request.getProjectDocumentUrl())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        request.setName(request.getName().trim());
        request.setProjectDocumentUrl(request.getProjectDocumentUrl().trim());
    }

    private boolean isHttps(String value) {
        if (value == null || value.isBlank() || value.length() > 1024) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private PromotionProject requireProject(Long id) {
        PromotionProject project = projectMapper.findById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_NOT_FOUND);
        }
        return project;
    }

    private void apply(PromotionProject project, UpsertPromotionProjectDTO request) {
        project.setName(request.getName());
        project.setProjectDocumentUrl(request.getProjectDocumentUrl());
        project.setStatus(request.getStatus() == null ? PromotionProjectStatus.ENABLED : request.getStatus());
        project.setSortOrder(request.getSortOrder());
    }

    private PromotionProjectVO toVO(PromotionProject project) {
        return PromotionProjectVO.builder()
                .id(project.getId())
                .name(project.getName())
                .coverImageUrl(project.getCoverImageUrl())
                .projectDocumentUrl(project.getProjectDocumentUrl())
                .status(project.getStatus())
                .sortOrder(project.getSortOrder())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private PromotionProjectCardVO toCardVO(PromotionProject project) {
        return PromotionProjectCardVO.builder()
                .id(project.getId())
                .name(project.getName())
                .coverImageUrl(project.getCoverImageUrl())
                .projectDocumentUrl(project.getProjectDocumentUrl())
                .sortOrder(project.getSortOrder())
                .build();
    }

    private void registerCoverCompletion(String newCover, String previousCover) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (previousCover != null) {
                coverStorageService.deleteIfLocal(previousCover);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousCover != null) {
                        coverStorageService.deleteIfLocal(previousCover);
                    }
                } else if (newCover != null) {
                    coverStorageService.deleteIfLocal(newCover);
                }
            }
        });
    }
}
