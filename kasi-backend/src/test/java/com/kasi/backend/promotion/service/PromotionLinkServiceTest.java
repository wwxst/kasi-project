package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.CreatePromotionLinkDTO;
import com.kasi.backend.promotion.entity.PromotionLink;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.enums.PromotionLinkStatus;
import com.kasi.backend.promotion.service.impl.PromotionLinkServiceImpl;
import com.kasi.backend.provider.exception.ProviderTransientException;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import com.kasi.backend.provider.spi.PromotionLinkRequest;
import com.kasi.backend.provider.spi.PromotionLinkResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionLinkServiceTest {
    @Mock PromotionLinkPersistenceService persistenceService;
    @Mock PromotionLinkProviderAdapter adapter;
    @InjectMocks PromotionLinkServiceImpl service;

    @Test
    @DisplayName("一个媒体平台一次生成落地页和OneLink两条链接")
    void createsSelectedVariantForOnePlatform() {
        CreatePromotionLinkDTO request = request(List.of("TIKTOK"));
        ProviderRuntimeConnection runtime = runtime();
        PromotionLink landing = link(1L, "LANDING", "track-1");
        when(persistenceService.prepareBatchPending(7L, request)).thenReturn(List.of(
                new PromotionLinkPreparation(landing, runtime, new PromotionLinkRequest("book", "track-1", MediaType.TIKTOK, "LANDING"))));
        when(adapter.generatePromotionLink(any(), any())).thenReturn(new PromotionLinkResult("code", "https://url", "track"));
        when(persistenceService.markSuccess(any(), any(), any(), any(), eq(7L), eq(request.getRequestKey()), any(), any()))
                .thenAnswer(invocation -> {
                    PromotionLink stored = new PromotionLink();
                    stored.setId(invocation.getArgument(0)); stored.setBatchNo("batch"); stored.setMediaType("TIKTOK");
                    stored.setLinkVariant(invocation.getArgument(7)); stored.setStatus(PromotionLinkStatus.SUCCESS);
                    return stored;
                });

        var result = service.createOrRetry(7L, request);
        assertThat(result.getLinks()).hasSize(1);
        assertThat(result.isComplete()).isTrue();
        verify(adapter).generatePromotionLink(any(), any());
    }

    @Test
    @DisplayName("一个变体远程失败时保留其它成功结果并标记不完整")
    void keepsPartialFailure() {
        CreatePromotionLinkDTO request = request(List.of("TIKTOK"));
        ProviderRuntimeConnection runtime = runtime();
        PromotionLink landing = link(1L, "LANDING", "track-1");
        PromotionLink onelink = link(2L, "ONELINK", "track-2");
        when(persistenceService.prepareBatchPending(7L, request)).thenReturn(List.of(
                new PromotionLinkPreparation(landing, runtime, new PromotionLinkRequest("book", "track-1", MediaType.TIKTOK, "LANDING")),
                new PromotionLinkPreparation(onelink, runtime, new PromotionLinkRequest("book", "track-2", MediaType.TIKTOK, "ONELINK"))));
        when(adapter.generatePromotionLink(any(), any())).thenReturn(new PromotionLinkResult("code", "https://url", "track"))
                .thenThrow(new ProviderTransientException("timeout"));
        when(persistenceService.markSuccess(any(), any(), any(), any(), eq(7L), eq(request.getRequestKey()), any(), any()))
                .thenReturn(successLink());

        var result = service.createOrRetry(7L, request);
        assertThat(result.isComplete()).isFalse();
        assertThat(result.getLinks()).extracting("status").containsExactly(PromotionLinkStatus.SUCCESS, PromotionLinkStatus.FAILED);
        verify(persistenceService).markFailed(2L, "PROVIDER_REMOTE_UNAVAILABLE", "timeout");
    }

    private CreatePromotionLinkDTO request(List<String> mediaTypes) {
        CreatePromotionLinkDTO request = new CreatePromotionLinkDTO();
        request.setProviderId(1L); request.setDramaId(23L); request.setMediaTypes(mediaTypes);
        request.setRequestKey("123e4567-e89b-12d3-a456-426614174000");
        return request;
    }

    private PromotionLink link(Long id, String variant, String tracking) {
        PromotionLink link = new PromotionLink(); link.setId(id); link.setBatchNo("batch");
        link.setMediaType("TIKTOK"); link.setLinkVariant(variant); link.setTrackingNo(tracking); link.setStatus(PromotionLinkStatus.PENDING); return link;
    }

    private ProviderRuntimeConnection runtime() {
        return new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
    }

    private PromotionLink successLink() {
        PromotionLink link = link(1L, "LANDING", "track-1");
        link.setStatus(PromotionLinkStatus.SUCCESS);
        return link;
    }
}
