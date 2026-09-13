package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.dto.PromotionProjectPageQueryDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectDTO;
import com.kasi.backend.promotion.dto.UpsertPromotionProjectTypeDTO;
import com.kasi.backend.promotion.entity.PromotionProject;
import com.kasi.backend.promotion.entity.PromotionProjectType;
import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import com.kasi.backend.promotion.mapper.PromotionProjectMapper;
import com.kasi.backend.promotion.mapper.PromotionProjectTypeMapper;
import com.kasi.backend.promotion.service.impl.PromotionProjectServiceImpl;
import com.kasi.backend.promotion.service.impl.PromotionProjectTypeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PromotionProjectServiceTest {

    @Mock
    private PromotionProjectMapper projectMapper;
    @Mock
    private PromotionProjectTypeMapper projectTypeMapper;
    @Mock
    private PromotionProjectCoverStorageService coverStorageService;

    @Test
    void createsTrimmedEnabledProject() {
        PromotionProjectService service = service();
        UpsertPromotionProjectDTO request = request("  项目A  ", "https://example.com/doc", 10);
        MockMultipartFile cover = new MockMultipartFile("coverFile", "cover.png", "image/png", "png".getBytes());
        when(coverStorageService.store(cover)).thenReturn("/uploads/promotion-projects/a.png");
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(projectType());
        when(projectMapper.insert(any())).thenAnswer(invocation -> {
            PromotionProject project = invocation.getArgument(0);
            project.setId(1L);
            return 1;
        });

        var created = service.create(request, cover);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getName()).isEqualTo("项目A");
        assertThat(created.getProjectTypeCode()).isEqualTo("CPA");
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
        when(projectMapper.findByIdForUpdate(1L)).thenReturn(stored);
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(projectType());

        UpsertPromotionProjectDTO request = request("新项目", "https://example.com/new", 5);
        request.setStatus(PromotionProjectStatus.DISABLED);
        var updated = service.update(1L, request, null);

        assertThat(updated.getCoverImageUrl()).isEqualTo("/uploads/promotion-projects/old.png");
        assertThat(updated.getStatus()).isEqualTo(PromotionProjectStatus.DISABLED);
        verify(coverStorageService, never()).store(any());
        verify(projectMapper).update(stored);
        InOrder order = inOrder(projectTypeMapper, projectMapper);
        order.verify(projectTypeMapper).findByIdForUpdate(1L);
        order.verify(projectMapper).findByIdForUpdate(1L);
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

    @Test
    void managesProjectTypesAndRejectsDeletingAnAssignedType() {
        PromotionProjectTypeService service = new PromotionProjectTypeServiceImpl(
                projectTypeMapper, projectMapper);
        UpsertPromotionProjectTypeDTO request = new UpsertPromotionProjectTypeDTO();
        request.setCode(" cpa ");
        request.setName(" 按行动付费 ");
        request.setSortOrder(1);
        when(projectTypeMapper.insert(any())).thenAnswer(invocation -> {
            PromotionProjectType projectType = invocation.getArgument(0);
            projectType.setId(1L);
            return 1;
        });

        var created = service.create(request);

        assertThat(created.getCode()).isEqualTo("CPA");
        assertThat(created.getName()).isEqualTo("按行动付费");
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(projectType());
        when(projectMapper.findIdsByProjectTypeIdForUpdate(1L)).thenReturn(List.of(7L));
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROMOTION_PROJECT_TYPE_IN_USE.getCode());
        InOrder order = inOrder(projectTypeMapper, projectMapper);
        order.verify(projectTypeMapper).findByIdForUpdate(1L);
        order.verify(projectMapper).findIdsByProjectTypeIdForUpdate(1L);
    }

    @Test
    void mapsCreateAndUpdateCodeDuplicateKeyToBusinessError() {
        PromotionProjectTypeService service = new PromotionProjectTypeServiceImpl(
                projectTypeMapper, projectMapper);
        UpsertPromotionProjectTypeDTO create = typeRequest("CPA");
        when(projectTypeMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate code"));
        assertThatThrownBy(() -> service.create(create))
                .extracting("code")
                .isEqualTo(ErrorCode.PROMOTION_PROJECT_TYPE_CODE_EXISTS.getCode());

        UpsertPromotionProjectTypeDTO update = typeRequest("CPM");
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(projectType());
        when(projectTypeMapper.update(any())).thenThrow(new DuplicateKeyException("duplicate code"));
        assertThatThrownBy(() -> service.update(1L, update))
                .extracting("code")
                .isEqualTo(ErrorCode.PROMOTION_PROJECT_TYPE_CODE_EXISTS.getCode());
    }

    @Test
    void allowsKeepingDisabledTypeButRejectsChangingToDisabledType() {
        PromotionProjectService service = service();
        PromotionProject stored = project(1L, "旧项目", "/uploads/promotion-projects/old.png",
                PromotionProjectStatus.ENABLED, 20);
        PromotionProjectType disabledCurrent = projectType();
        disabledCurrent.setStatus(PromotionProjectStatus.DISABLED);
        when(projectTypeMapper.findByIdForUpdate(1L)).thenReturn(disabledCurrent);
        when(projectMapper.findByIdForUpdate(1L)).thenReturn(stored);
        service.update(1L, request("保留停用类型", "https://example.com/doc", 1), null);

        PromotionProjectType disabledOther = projectType();
        disabledOther.setId(2L);
        disabledOther.setStatus(PromotionProjectStatus.DISABLED);
        when(projectTypeMapper.findByIdForUpdate(2L)).thenReturn(disabledOther);
        UpsertPromotionProjectDTO changed = request("改选停用类型", "https://example.com/doc", 1);
        changed.setProjectTypeId(2L);
        assertThatThrownBy(() -> service.update(1L, changed, null))
                .extracting("code")
                .isEqualTo(ErrorCode.PROMOTION_PROJECT_TYPE_DISABLED.getCode());
    }

    private static UpsertPromotionProjectTypeDTO typeRequest(String code) {
        UpsertPromotionProjectTypeDTO request = new UpsertPromotionProjectTypeDTO();
        request.setCode(code);
        request.setName("项目类型");
        request.setSortOrder(1);
        return request;
    }

    private PromotionProjectService service() {
        return new PromotionProjectServiceImpl(projectMapper, projectTypeMapper, coverStorageService);
    }

    private static UpsertPromotionProjectDTO request(String name, String url, int sortOrder) {
        UpsertPromotionProjectDTO request = new UpsertPromotionProjectDTO();
        request.setProjectTypeId(1L);
        request.setName(name);
        request.setProjectDocumentUrl(url);
        request.setSortOrder(sortOrder);
        return request;
    }

    private static PromotionProject project(Long id, String name, String cover,
                                            PromotionProjectStatus status, int sortOrder) {
        PromotionProject project = new PromotionProject();
        project.setId(id);
        project.setProjectTypeId(1L);
        project.setName(name);
        project.setCoverImageUrl(cover);
        project.setProjectDocumentUrl("https://example.com/doc");
        project.setStatus(status);
        project.setSortOrder(sortOrder);
        return project;
    }

    private static PromotionProjectType projectType() {
        PromotionProjectType projectType = new PromotionProjectType();
        projectType.setId(1L);
        projectType.setCode("CPA");
        projectType.setName("按行动付费");
        projectType.setStatus(PromotionProjectStatus.ENABLED);
        projectType.setSortOrder(1);
        return projectType;
    }
}
