package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        failed.setProviderId(1L);
        failed.setDramaId(23L);
        failed.setMediaType("TIKTOK");
        failed.setLinkVariant("LANDING");
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

    @Test
    @DisplayName("相同requestKey不能变更原推广任务的请求内容")
    void reusedRequestKeyRejectsDifferentTaskIdentity() {
        PromotionUser user = new PromotionUser();
        user.setStatus(1);
        user.setUserNo("583729104628");
        PromotionLink failed = new PromotionLink();
        failed.setId(41L);
        failed.setProviderId(1L);
        failed.setDramaId(23L);
        failed.setMediaType("TIKTOK");
        failed.setLinkVariant("LANDING");
        failed.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.FAILED);
        when(userMapper.findById(7L)).thenReturn(user);
        when(linkMapper.findBatchByUserAndRequestKey(7L, "request")).thenReturn(List.of(failed));

        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(1L);
        request.setDramaId(24L);
        request.setMediaTypes(List.of("TIKTOK"));
        request.setLinkVariant("LANDING");
        request.setRequestKey("request");

        assertThatThrownBy(() -> new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService).prepareBatchPending(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(7014);
        verifyNoInteractions(dramaMapper, runtimeService);
    }

    @Test
    @DisplayName("相同requestKey不能增加媒体平台或变更链接类型")
    void reusedRequestKeyRejectsDifferentMediaSetOrVariant() {
        PromotionUser user = new PromotionUser();
        user.setStatus(1);
        PromotionLink failed = new PromotionLink();
        failed.setProviderId(1L);
        failed.setDramaId(23L);
        failed.setMediaType("TIKTOK");
        failed.setLinkVariant("LANDING");
        when(userMapper.findById(7L)).thenReturn(user);
        when(linkMapper.findBatchByUserAndRequestKey(7L, "request")).thenReturn(List.of(failed));
        PromotionLinkPersistenceServiceImpl service = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService);

        CreatePromotionLinkDTO changedMedia = request(1L, 23L, List.of("TIKTOK", "YOUTUBE"), "LANDING");
        CreatePromotionLinkDTO changedVariant = request(1L, 23L, List.of("TIKTOK"), "ONELINK");

        assertThatThrownBy(() -> service.prepareBatchPending(7L, changedMedia))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(7014);
        assertThatThrownBy(() -> service.prepareBatchPending(7L, changedVariant))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(7014);
    }

    @Test
    @DisplayName("GoodShort返回相同口令时复用已有成功链接并删除新建占位记录")
    void sameExternalCodeReusesExistingLink() {
        PromotionLink pending = link(42L, PromotionLinkStatus.PENDING);
        PromotionLink existing = link(41L, PromotionLinkStatus.SUCCESS);
        existing.setExternalCode("CODE-1");
        when(linkMapper.findByUserAndRequestKey(7L, "request", "TIKTOK", "LANDING"))
                .thenReturn(pending);
        when(linkMapper.findSuccessfulByIdentity(3L, 23L, 7L, "CODE-1"))
                .thenReturn(existing);
        when(linkMapper.deleteById(42L)).thenReturn(1);

        PromotionLink result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService)
                .markSuccess(42L, "CODE-1", "https://example.test/new",
                        7L, "request", "TIKTOK", "LANDING");

        assertThat(result).isSameAs(existing);
        verify(linkMapper).deleteById(42L);
        verify(linkMapper, never()).markSuccess(42L, "CODE-1", "https://example.test/new");
    }

    @Test
    @DisplayName("并发写入相同口令被唯一键拒绝后复用已成功链接")
    void duplicateExternalCodeAfterConcurrentSuccessReusesExistingLink() {
        PromotionLink pending = link(42L, PromotionLinkStatus.PENDING);
        PromotionLink existing = link(41L, PromotionLinkStatus.SUCCESS);
        existing.setExternalCode("CODE-1");
        when(linkMapper.findByUserAndRequestKey(7L, "request", "TIKTOK", "LANDING"))
                .thenReturn(pending);
        when(linkMapper.findSuccessfulByIdentity(3L, 23L, 7L, "CODE-1"))
                .thenReturn(null, existing);
        when(linkMapper.markSuccess(42L, "CODE-1", "https://example.test/new"))
                .thenThrow(new DuplicateKeyException("duplicate external code"));
        when(linkMapper.deleteById(42L)).thenReturn(1);

        PromotionLink result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService)
                .markSuccess(42L, "CODE-1", "https://example.test/new",
                        7L, "request", "TIKTOK", "LANDING");

        assertThat(result).isSameAs(existing);
        verify(linkMapper).deleteById(42L);
    }

    private PromotionLink link(Long id, PromotionLinkStatus status) {
        PromotionLink link = new PromotionLink();
        link.setId(id);
        link.setUserId(7L);
        link.setConnectionId(3L);
        link.setDramaId(23L);
        link.setStatus(status);
        link.setTrackingNo("tracking-" + id);
        return link;
    }

    private CreatePromotionLinkDTO request(Long providerId, Long dramaId,
                                           List<String> mediaTypes, String linkVariant) {
        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(providerId);
        request.setDramaId(dramaId);
        request.setMediaTypes(mediaTypes);
        request.setLinkVariant(linkVariant);
        request.setRequestKey("request");
        return request;
    }
}
