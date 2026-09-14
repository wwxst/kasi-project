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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @DisplayName("每个媒体平台固定准备落地页和OneLink")
    void preparesBothVariantsForEveryPlatform() {
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
        when(linkMapper.findByUserAndRequestKeyForUpdate(eq(7L), eq("request"), any(), any()))
                .thenReturn(null);

        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(1L);
        request.setDramaId(23L);
        request.setMediaTypes(List.of("TIKTOK", "YOUTUBE"));
        request.setRequestKey("request");

        List<PromotionLinkPreparation> result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService).prepareBatchPending(7L, request);

        assertThat(result)
                .extracting(preparation -> preparation.link().getMediaType()
                        + "/" + preparation.link().getLinkVariant())
                .containsExactly("TIKTOK/LANDING", "TIKTOK/ONELINK", "YOUTUBE/LANDING", "YOUTUBE/ONELINK");
        assertThat(result)
                .extracting(preparation -> preparation.providerRequest().userNo())
                .containsOnly("583729104628");
        assertThat(result)
                .extracting(preparation -> preparation.link().getTrackingNo())
                .doesNotContainNull()
                .doesNotHaveDuplicates();
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
        PromotionLink successful = new PromotionLink();
        successful.setId(42L);
        successful.setProviderId(1L);
        successful.setDramaId(23L);
        successful.setMediaType("TIKTOK");
        successful.setLinkVariant("ONELINK");
        successful.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.SUCCESS);
        when(userMapper.findById(7L)).thenReturn(user);
        when(dramaMapper.findById(23L)).thenReturn(drama);
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK))
                .thenReturn(new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", null, null));
        when(linkMapper.findBatchByUserAndRequestKey(7L, "request")).thenReturn(List.of(failed, successful));
        when(linkMapper.findByUserAndRequestKeyForUpdate(eq(7L), eq("request"), eq("TIKTOK"), eq("LANDING")))
                .thenReturn(failed);
        when(linkMapper.findByUserAndRequestKeyForUpdate(eq(7L), eq("request"), eq("TIKTOK"), eq("ONELINK")))
                .thenReturn(successful);

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
        request.setRequestKey("request");

        assertThatThrownBy(() -> new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService).prepareBatchPending(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(7014);
        verifyNoInteractions(dramaMapper, runtimeService);
    }

    @Test
    @DisplayName("相同requestKey拒绝不完整的双变体集合")
    void reusedRequestKeyRejectsIncompleteVariantSet() {
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

        CreatePromotionLinkDTO request = request(1L, 23L, List.of("TIKTOK"));

        assertThatThrownBy(() -> service.prepareBatchPending(7L, request))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(7014);
        verifyNoInteractions(dramaMapper, runtimeService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"LANDING", "ONELINK"})
    @DisplayName("GoodShort同类型重复返回相同口令时复用成功链接并删除新占位")
    void sameExternalCodeReusesExistingLink(String linkVariant) {
        PromotionLink pending = link(42L, PromotionLinkStatus.PENDING);
        PromotionLink existing = link(41L, PromotionLinkStatus.SUCCESS);
        pending.setLinkVariant(linkVariant);
        existing.setLinkVariant(linkVariant);
        existing.setExternalCode("CODE-1");
        when(linkMapper.findByUserAndRequestKey(7L, "request", "TIKTOK", linkVariant))
                .thenReturn(pending);
        when(linkMapper.findSuccessfulByIdentity(3L, 23L, 7L, "TIKTOK", "CODE-1", linkVariant))
                .thenReturn(existing);
        when(linkMapper.deleteById(42L)).thenReturn(1);

        PromotionLink result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService)
                .markSuccess(42L, "CODE-1", "https://example.test/new",
                        7L, "request", "TIKTOK", linkVariant);

        assertThat(result).isSameAs(existing);
        verify(linkMapper).deleteById(42L);
        verify(linkMapper, never()).markSuccess(42L, "CODE-1", "https://example.test/new");
    }

    @ParameterizedTest(name = "已有{0}，新增{1}")
    @CsvSource({"LANDING,ONELINK", "ONELINK,LANDING"})
    @DisplayName("相同口令的两个链接变体按任意顺序各自保存")
    void sameExternalCodeAcrossVariantsKeepsBothLinks(String existingVariant, String currentVariant) {
        PromotionLink pending = link(42L, PromotionLinkStatus.PENDING);
        pending.setLinkVariant(currentVariant);
        PromotionLink existing = link(41L, PromotionLinkStatus.SUCCESS);
        existing.setLinkVariant(existingVariant);
        existing.setExternalCode("CODE-1");
        pending.setExternalCode("CODE-1");
        pending.setShareUrl("https://example.test/one");
        when(linkMapper.findByUserAndRequestKey(7L, "request", "TIKTOK", currentVariant))
                .thenReturn(pending);
        when(linkMapper.findSuccessfulByIdentity(3L, 23L, 7L, "TIKTOK", "CODE-1", currentVariant))
                .thenReturn(null);
        when(linkMapper.markSuccess(42L, "CODE-1", "https://example.test/one"))
                .thenReturn(1);

        PromotionLink result = new PromotionLinkPersistenceServiceImpl(
                linkMapper, userMapper, dramaMapper, runtimeService)
                .markSuccess(42L, "CODE-1", "https://example.test/one",
                        7L, "request", "TIKTOK", currentVariant);

        assertThat(result).isSameAs(pending);
        verify(linkMapper, never()).deleteById(42L);
        verify(linkMapper).markSuccess(42L, "CODE-1", "https://example.test/one");
    }

    @Test
    @DisplayName("并发写入相同口令被唯一键拒绝后复用已成功链接")
    void duplicateExternalCodeAfterConcurrentSuccessReusesExistingLink() {
        PromotionLink pending = link(42L, PromotionLinkStatus.PENDING);
        PromotionLink existing = link(41L, PromotionLinkStatus.SUCCESS);
        existing.setExternalCode("CODE-1");
        when(linkMapper.findByUserAndRequestKey(7L, "request", "TIKTOK", "LANDING"))
                .thenReturn(pending);
        when(linkMapper.findSuccessfulByIdentity(3L, 23L, 7L, "TIKTOK", "CODE-1", "LANDING"))
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
                                           List<String> mediaTypes) {
        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(providerId);
        request.setDramaId(dramaId);
        request.setMediaTypes(mediaTypes);
        request.setRequestKey("request");
        return request;
    }
}
