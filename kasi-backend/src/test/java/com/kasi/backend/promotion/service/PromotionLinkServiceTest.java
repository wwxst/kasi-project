package com.kasi.backend.promotion.service;

import com.kasi.backend.drama.entity.ProviderCommissionRule;
import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.drama.service.ProviderCommissionRuleService;
import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaAccountStatus;
import com.kasi.backend.promotion.service.impl.PromotionLinkServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import com.kasi.backend.provider.spi.PromotionLinkResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.entity.PromotionUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionLinkServiceTest {
    @Mock PromotionLinkPersistenceService persistenceService;
    @Mock PromotionLinkProviderAdapter adapter;
    @InjectMocks PromotionLinkServiceImpl service;

    @Test
    @DisplayName("已加白且短剧已上架时生成推广链接并保存平台结果")
    void createsLinkWhenEligible() {
        var request = request();
        var runtime = new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        var link = new com.kasi.backend.promotion.entity.PromotionLink();
        link.setId(99L); link.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.PENDING);
        link.setTrackingNo("tracking-no");
        var preparation = new PromotionLinkPreparation(link, runtime,
                new com.kasi.backend.provider.spi.PromotionLinkRequest("book-23", "tracking-no",
                        com.kasi.backend.promotion.enums.MediaType.TIKTOK, "DEFAULT"));
        when(adapter.generatePromotionLink(any(), any())).thenReturn(new PromotionLinkResult("123456", "https://demo/link", "tracking"));
        var stored = new com.kasi.backend.promotion.entity.PromotionLink(); stored.setId(99L); stored.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.SUCCESS); stored.setExternalCode("123456"); stored.setShareUrl("https://demo/link"); stored.setTrackingNo("tracking");
        when(persistenceService.preparePending(7L, request)).thenReturn(preparation);
        when(persistenceService.markSuccess(99L, "123456", "https://demo/link", "tracking", 7L,
                request.getRequestKey())).thenReturn(stored);

        assertThat(service.createOrRetry(7L, request)).isNotNull();
        var order = inOrder(persistenceService, adapter);
        order.verify(persistenceService).preparePending(7L, request);
        order.verify(adapter).generatePromotionLink(any(), any());
        order.verify(persistenceService).markSuccess(99L, "123456", "https://demo/link", "tracking", 7L,
                request.getRequestKey());
    }

    @Test
    @DisplayName("未加白媒体账号不能调用平台生成接口")
    void rejectsUnapprovedMedia() {
        var request = request();
        when(persistenceService.preparePending(7L, request())).thenThrow(
                new com.kasi.backend.common.exception.BusinessException(
                        com.kasi.backend.common.exception.ErrorCode.PROMOTION_LINK_MEDIA_NOT_APPROVED));

        assertThatThrownBy(() -> service.createOrRetry(7L, request()))
                .hasMessageContaining("尚未在该平台加白");
        verifyNoInteractions(adapter);
    }

    @Test
    @DisplayName("远程失败先落库FAILED再返回业务错误")
    void marksFailedBeforePropagatingRemoteError() {
        var request = request();
        var link = new com.kasi.backend.promotion.entity.PromotionLink();
        link.setId(99L); link.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.PENDING);
        link.setTrackingNo("tracking-no");
        var runtime = runtime();
        when(persistenceService.preparePending(eq(7L), eq(request))).thenReturn(new PromotionLinkPreparation(
                link, runtime, new com.kasi.backend.provider.spi.PromotionLinkRequest("book-23", "tracking-no",
                com.kasi.backend.promotion.enums.MediaType.TIKTOK, "DEFAULT")));
        when(adapter.generatePromotionLink(any(), any())).thenThrow(new com.kasi.backend.provider.exception.ProviderTransientException("timeout"));

        assertThatThrownBy(() -> service.createOrRetry(7L, request))
                .hasMessageContaining("短剧平台暂时不可用");

        var order = inOrder(persistenceService, adapter);
        order.verify(persistenceService).preparePending(7L, request);
        order.verify(adapter).generatePromotionLink(any(), any());
        order.verify(persistenceService).markFailed(99L, "PROVIDER_REMOTE_UNAVAILABLE", "timeout");
    }

    private CreatePromotionLinkDTO request() {
        var request = new CreatePromotionLinkDTO(); request.setProviderId(1L); request.setDramaId(23L); request.setMediaAccountId(8L);
        request.setRequestKey("123e4567-e89b-12d3-a456-426614174000"); request.setLandingType("DEFAULT"); return request;
    }
    private ProviderRuntimeConnection runtime() { return new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter); }
}
