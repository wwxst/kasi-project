package com.kasi.backend.promotion.service;

import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.service.impl.PromotionLinkPersistenceServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionLinkPersistenceServiceTest {
    @Mock private PromotionLinkMapper linkMapper;
    @Mock private PromotionUserMapper userMapper;
    @Mock private ProviderDramaMapper dramaMapper;
    @Mock private ProviderRuntimeConnectionService runtimeService;

    @Test
    @DisplayName("指定OneLink时每个媒体平台只准备一个OneLink任务")
    void preparesOnlySelectedVariant() {
        PromotionUser user = new PromotionUser();
        user.setStatus(1);
        user.setUserNo("583729104628");
        ProviderDrama drama = new ProviderDrama();
        drama.setId(23L);
        drama.setConnectionId(3L);
        drama.setExternalDramaId("book");
        drama.setLocalStatus(DramaLocalStatus.PUBLISHED);
        drama.setRemoteShowStatus("1");
        when(userMapper.findById(7L)).thenReturn(user);
        when(dramaMapper.findById(23L)).thenReturn(drama);
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK))
                .thenReturn(new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", null, null));
        when(linkMapper.findBatchByUserAndRequestKey(7L, "request")).thenReturn(List.of());
        when(linkMapper.findByUserAndRequestKeyForUpdate(eq(7L), eq("request"), eq("TIKTOK"), any()))
                .thenReturn(null);

        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(1L);
        request.setDramaId(23L);
        request.setMediaTypes(List.of("TIKTOK"));
        request.setRequestKey("request");
        request.setLinkVariant("ONELINK");

        List<PromotionLinkPreparation> result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService).prepareBatchPending(7L, request);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().link().getLinkVariant()).isEqualTo("ONELINK");
        assertThat(result.getFirst().providerRequest().linkVariant()).isEqualTo("ONELINK");
        assertThat(result.getFirst().providerRequest().userNo()).isEqualTo("583729104628");
    }

    @Test
    @DisplayName("失败链接重试时仍使用同一用户编号作为GoodShort customParams")
    void retryKeepsUserNoAsProviderCustomParams() {
        PromotionUser user = new PromotionUser();
        user.setStatus(1);
        user.setUserNo("583729104628");
        ProviderDrama drama = new ProviderDrama();
        drama.setId(23L);
        drama.setConnectionId(3L);
        drama.setExternalDramaId("book");
        drama.setLocalStatus(DramaLocalStatus.PUBLISHED);
        drama.setRemoteShowStatus("1");
        PromotionLink failed = new PromotionLink();
        failed.setId(41L);
        failed.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.FAILED);
        failed.setTrackingNo("old-tracking");
        when(userMapper.findById(7L)).thenReturn(user);
        when(dramaMapper.findById(23L)).thenReturn(drama);
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK))
                .thenReturn(new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", null, null));
        when(linkMapper.findBatchByUserAndRequestKey(7L, "request")).thenReturn(List.of(failed));
        when(linkMapper.findByUserAndRequestKeyForUpdate(eq(7L), eq("request"), eq("TIKTOK"), eq("LANDING")))
                .thenReturn(failed);

        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(1L);
        request.setDramaId(23L);
        request.setMediaTypes(List.of("TIKTOK"));
        request.setRequestKey("request");

        List<PromotionLinkPreparation> result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService).prepareBatchPending(7L, request);

        assertThat(result.getFirst().providerRequest().userNo()).isEqualTo("583729104628");
    }
}
