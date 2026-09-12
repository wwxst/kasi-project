package com.kasi.backend.drama.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.dto.DramaPageQueryDTO;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.entity.ProviderDramaContent;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.enums.PromotionCommissionScope;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.impl.DramaCatalogAdminServiceImpl;
import com.kasi.backend.drama.service.impl.DramaLanguageServiceImpl;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("短剧目录管理员服务")
class DramaCatalogAdminServiceTest {
    private ProviderDramaMapper dramaMapper;
    private ShortDramaConnectionMapper connectionMapper;
    private DramaCatalogAdminService service;

    @BeforeEach
    void setUp() {
        dramaMapper = mock(ProviderDramaMapper.class);
        connectionMapper = mock(ShortDramaConnectionMapper.class);
        service = new DramaCatalogAdminServiceImpl(dramaMapper, connectionMapper,
                new DramaLanguageServiceImpl(new com.kasi.backend.drama.config.DramaSyncProperties()));
    }

    @Test
    @DisplayName("分页查询使用平台连接过滤并返回目录展示字段")
    void getPageUsesConnectionAndMapsItems() {
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(3L);
        when(connectionMapper.findByProviderId(7L)).thenReturn(connection);
        DramaPageQueryDTO query = new DramaPageQueryDTO();
        query.setProviderId(7L); query.setPage(2); query.setSize(10); query.setTitle("Time");
        query.setLanguage("ENGLISH"); query.setLocalStatus(DramaLocalStatus.PUBLISHED);
        when(dramaMapper.count(3L, "Time", "ENGLISH", null, DramaLocalStatus.PUBLISHED)).thenReturn(11L);
        ProviderDrama drama = drama();
        when(dramaMapper.page(3L, "Time", "ENGLISH", null, DramaLocalStatus.PUBLISHED, 10, 10))
                .thenReturn(List.of(drama));

        var page = service.getPage(query);

        assertThat(page.getTotal()).isEqualTo(11);
        assertThat(page.getList()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(21L);
            assertThat(item.getTitle()).isEqualTo("Time Story");
            assertThat(item.getLanguageLabel()).isEqualTo("英语");
            assertThat(item.getLocalStatus()).isEqualTo(DramaLocalStatus.PUBLISHED);
        });
    }

    @Test
    @DisplayName("详情返回剧集且不包含平台连接或密钥字段")
    void getByIdReturnsContents() {
        ProviderDrama drama = drama();
        ProviderDramaContent content = new ProviderDramaContent();
        content.setId(31L); content.setDramaId(21L); content.setSequenceNo(1); content.setFree(true);
        when(dramaMapper.findById(21L)).thenReturn(drama);
        when(dramaMapper.findContents(21L)).thenReturn(List.of(content));

        var detail = service.getById(21L);

        assertThat(detail.getContents()).singleElement().satisfies(item -> {
            assertThat(item.getSequenceNo()).isEqualTo(1);
            assertThat(item.isFree()).isTrue();
        });
    }

    @Test
    @DisplayName("本地上下架只允许已发布和已下架且短剧必须存在")
    void updateLocalStatusValidatesTargetAndExistence() {
        when(dramaMapper.findById(21L)).thenReturn(drama());
        when(dramaMapper.updateLocalStatus(21L, DramaLocalStatus.OFFLINE)).thenReturn(1);

        service.updateLocalStatus(21L, DramaLocalStatus.OFFLINE);

        verify(dramaMapper).updateLocalStatus(21L, DramaLocalStatus.OFFLINE);
        assertThatThrownBy(() -> service.updateLocalStatus(21L, DramaLocalStatus.DRAFT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateLocalStatus(99L, DramaLocalStatus.PUBLISHED))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest(name = "远端状态 {0} 不允许上架")
    @NullSource
    @ValueSource(strings = {"MISSING", "0", "OFFLINE"})
    @DisplayName("远端非在线短剧不能上架")
    void remoteUnavailableDramaCannotBePublished(String remoteShowStatus) {
        ProviderDrama drama = drama();
        drama.setRemoteShowStatus(remoteShowStatus);
        drama.setLocalStatus(DramaLocalStatus.OFFLINE);
        when(dramaMapper.findById(21L)).thenReturn(drama);
        when(dramaMapper.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED)).thenReturn(1);
        when(dramaMapper.findContents(21L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6018));

        verify(dramaMapper, never()).updateLocalStatus(21L, DramaLocalStatus.PUBLISHED);
    }

    @Test
    @DisplayName("远端在线且本地下架的短剧仍允许上架")
    void remoteOnlineDramaCanBePublished() {
        ProviderDrama drama = drama();
        drama.setRemoteShowStatus("1");
        drama.setLocalStatus(DramaLocalStatus.OFFLINE);
        when(dramaMapper.findById(21L)).thenReturn(drama);
        when(dramaMapper.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED)).thenReturn(1);
        when(dramaMapper.findContents(21L)).thenReturn(List.of());

        service.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED);

        verify(dramaMapper).updateLocalStatus(21L, DramaLocalStatus.PUBLISHED);
    }

    @Test
    @DisplayName("远端状态在读取后变为非在线时上架返回远端状态错误")
    void remoteStatusChangeBeforeUpdatePreventsPublishing() {
        ProviderDrama drama = drama();
        drama.setRemoteShowStatus("1");
        drama.setLocalStatus(DramaLocalStatus.OFFLINE);
        when(dramaMapper.findById(21L)).thenReturn(drama);
        when(dramaMapper.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED)).thenReturn(0);

        assertThatThrownBy(() -> service.updateLocalStatus(21L, DramaLocalStatus.PUBLISHED))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(6018));

        verify(dramaMapper, never()).findContents(21L);
    }

    @Test
    @DisplayName("推广元数据更新会规范化范围并清理空说明")
    void updatePromotionMetadataNormalizesScopes() {
        when(dramaMapper.findById(21L)).thenReturn(drama());
        when(dramaMapper.updatePromotionMetadata(21L, "ORDER,AD", "说明")).thenReturn(1);

        service.updatePromotionMetadata(21L,
                List.of(PromotionCommissionScope.AD, PromotionCommissionScope.ORDER,
                        PromotionCommissionScope.AD), "  说明  ");

        verify(dramaMapper).updatePromotionMetadata(21L, "ORDER,AD", "说明");
    }

    private ProviderDrama drama() {
        ProviderDrama drama = new ProviderDrama();
        drama.setId(21L); drama.setConnectionId(3L); drama.setExternalDramaId("book-1");
        drama.setTitle("Time Story"); drama.setLanguage("ENGLISH");
        drama.setRemoteShowStatus("1"); drama.setLocalStatus(DramaLocalStatus.PUBLISHED);
        return drama;
    }
}
