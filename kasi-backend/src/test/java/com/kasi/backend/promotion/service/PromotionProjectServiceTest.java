package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.PromotionProjectPageQueryDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectDTO;
import com.kasi.backend.promotion.entity.PromotionProject;
import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import com.kasi.backend.promotion.mapper.PromotionProjectMapper;
import com.kasi.backend.promotion.service.impl.PromotionProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionProjectServiceTest {

    @Mock
    private PromotionProjectMapper projectMapper;
    @Mock
    private PromotionProjectCoverStorageService coverStorageService;

    @Test
    void createsTrimmedEnabledProject() {
        PromotionProjectService service = service();
        UpsertPromotionProjectDTO request = request("  项目A  ", "https://example.com/doc", 10);
        MockMultipartFile cover = new MockMultipartFile("coverFile", "cover.png", "image/png", "png".getBytes());
        when(coverStorageService.store(cover)).thenReturn("/uploads/promotion-projects/a.png");
        when(projectMapper.insert(any())).thenAnswer(invocation -> {
            PromotionProject project = invocation.getArgument(0);
            project.setId(1L);
            return 1;
        });

        var created = service.create(request, cover);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("项目A");
        assertThat(created.getStatus()).isEqualTo(PromotionProjectStatus.ENABLED);
        assertThat(created.getSortOrder()).isEqualTo(10);
        assertThat(created.getCoverImageUrl()).isEqualTo("/uploads/promotion-projects/a.png");
    }

    @Test
    void rejectsNonHttpsDocumentBeforeStoringCover() {
        PromotionProjectService service = service();
        UpsertPromotionProjectDTO request = request("项目A", "http://example.com/doc", 10);
        MockMultipartFile cover = new MockMultipartFile("coverFile", "cover.png", "image/png", "png".getBytes());

        assertThatThrownBy(() -> service.create(request, cover))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
        verify(coverStorageService, never()).store(any());
    }

    @Test
    void rejectsNegativeSortOrder() {
        PromotionProjectService service = service();
        UpsertPromotionProjectDTO request = request("项目A", "https://example.com/doc", -1);

        assertThatThrownBy(() -> service.create(request,
                new MockMultipartFile("coverFile", new byte[]{1})))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }

    @Test
    void updatesProjectAndKeepsCoverWhenNoNewFileIsProvided() {
        PromotionProjectService service = service();
        PromotionProject stored = project(1L, "旧项目", "/uploads/promotion-projects/old.png",
                PromotionProjectStatus.ENABLED, 20);
        when(projectMapper.findById(1L)).thenReturn(stored);

        UpsertPromotionProjectDTO request = request("新项目", "https://example.com/new", 5);
        request.setStatus(PromotionProjectStatus.DISABLED);
        var updated = service.update(1L, request, null);

        assertThat(updated.getCoverImageUrl()).isEqualTo("/uploads/promotion-projects/old.png");
        assertThat(updated.getStatus()).isEqualTo(PromotionProjectStatus.DISABLED);
        verify(coverStorageService, never()).store(any());
        verify(projectMapper).update(stored);
    }

    @Test
    void deletesProjectAndItsCover() {
        PromotionProjectService service = service();
        PromotionProject stored = project(1L, "项目A", "/uploads/promotion-projects/a.png",
                PromotionProjectStatus.ENABLED, 10);
        when(projectMapper.findById(1L)).thenReturn(stored);
        when(projectMapper.deleteById(1L)).thenReturn(1);

        service.delete(1L);

        verify(projectMapper).deleteById(1L);
        verify(coverStorageService).deleteIfLocal("/uploads/promotion-projects/a.png");
    }

    @Test
    void returnsNotFoundForMissingProject() {
        PromotionProjectService service = service();
        when(projectMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROMOTION_PROJECT_NOT_FOUND.getCode());
    }

    @Test
    void returnsPagedAdminProjectsAndEnabledUserProjects() {
        PromotionProjectService service = service();
        PromotionProjectPageQueryDTO query = new PromotionProjectPageQueryDTO();
        query.setPage(2);
        query.setSize(1);
        PromotionProject enabled = project(2L, "项目B", "/uploads/promotion-projects/b.png",
                PromotionProjectStatus.ENABLED, 10);
        when(projectMapper.countAll()).thenReturn(2L);
        when(projectMapper.findPage(1, 1)).thenReturn(List.of(enabled));
        when(projectMapper.findEnabled()).thenReturn(List.of(enabled));

        assertThat(service.getPage(query).getList()).hasSize(1);
        assertThat(service.getPage(query).getTotal()).isEqualTo(2);
        assertThat(service.listEnabled()).extracting("name").containsExactly("项目B");
    }

    private PromotionProjectService service() {
        return new PromotionProjectServiceImpl(projectMapper, coverStorageService);
    }

    private static UpsertPromotionProjectDTO request(String name, String url, int sortOrder) {
        UpsertPromotionProjectDTO request = new UpsertPromotionProjectDTO();
        request.setName(name);
        request.setProjectDocumentUrl(url);
        request.setSortOrder(sortOrder);
        return request;
    }

    private static PromotionProject project(Long id, String name, String cover,
                                            PromotionProjectStatus status, int sortOrder) {
        PromotionProject project = new PromotionProject();
        project.setId(id);
        project.setName(name);
        project.setCoverImageUrl(cover);
        project.setProjectDocumentUrl("https://example.com/doc");
        project.setStatus(status);
        project.setSortOrder(sortOrder);
        return project;
    }
}
