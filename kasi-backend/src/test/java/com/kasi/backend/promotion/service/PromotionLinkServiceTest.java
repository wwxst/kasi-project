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
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.impl.PromotionLinkServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.PromotionLinkProviderAdapter;
import com.kasi.backend.provider.spi.PromotionLinkResult;
import com.kasi.backend.provider.spi.ProviderConnectionSecret;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.mapper.PromotionUserMapper;
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
    @Mock PromotionLinkMapper linkMapper;
    @Mock PromotionUserMapper userMapper;
    @Mock PromotionMediaAccountMapper mediaMapper;
    @Mock ProviderMediaFilingMapper filingMapper;
    @Mock ProviderDramaMapper dramaMapper;
    @Mock ProviderRuntimeConnectionService runtimeService;
    @Mock ProviderCommissionRuleService ruleService;
    @Mock PromotionLinkProviderAdapter adapter;
    @InjectMocks PromotionLinkServiceImpl service;

    @Test
    @DisplayName("已加白且短剧已上架时生成推广链接并保存平台结果")
    void createsLinkWhenEligible() {
        var request = request();
        var user = new PromotionUser(); user.setStatus(1);
        var media = new PromotionMediaAccount(); media.setId(8L); media.setUserId(7L); media.setStatus(MediaAccountStatus.ENABLED.getCode());
        media.setMediaType(com.kasi.backend.promotion.enums.MediaType.TIKTOK);
        var drama = new ProviderDrama(); drama.setId(23L); drama.setConnectionId(3L); drama.setExternalDramaId("book-23");
        drama.setLocalStatus(DramaLocalStatus.PUBLISHED); drama.setRemoteShowStatus("1");
        var filing = new ProviderMediaFiling(); filing.setStatus(FilingStatus.APPROVED);
        var runtime = new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort",
                new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter);
        when(userMapper.findById(7L)).thenReturn(user);
        when(mediaMapper.findOwnedById(8L, 7L)).thenReturn(media);
        when(dramaMapper.findById(23L)).thenReturn(drama);
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK)).thenReturn(runtime);
        when(ruleService.findDefaultRule(1L)).thenReturn(new ProviderCommissionRule());
        when(filingMapper.findByConnectionAndMedia(3L, 8L)).thenReturn(filing);
        doAnswer(invocation -> { var link = invocation.getArgument(0, com.kasi.backend.promotion.entity.PromotionLink.class); link.setId(99L); return 1; }).when(linkMapper).insert(any());
        when(adapter.generatePromotionLink(any(), any())).thenReturn(new PromotionLinkResult("123456", "https://demo/link", "tracking"));
        var stored = new com.kasi.backend.promotion.entity.PromotionLink(); stored.setId(99L); stored.setStatus(com.kasi.backend.promotion.enums.PromotionLinkStatus.SUCCESS); stored.setExternalCode("123456"); stored.setShareUrl("https://demo/link"); stored.setTrackingNo("tracking");
        when(linkMapper.findByUserAndRequestKeyForUpdate(7L, request.getRequestKey())).thenReturn(null);
        when(linkMapper.findByUserAndRequestKey(7L, request.getRequestKey())).thenReturn(stored);

        assertThat(service.createOrRetry(7L, request)).isNotNull();
        verify(linkMapper).findByUserAndRequestKeyForUpdate(7L, request.getRequestKey());
        verify(linkMapper).markSuccess(99L, "123456", "https://demo/link", "tracking");
    }

    @Test
    @DisplayName("未加白媒体账号不能调用平台生成接口")
    void rejectsUnapprovedMedia() {
        var request = request();
        var user = new PromotionUser(); user.setStatus(1);
        var media = new PromotionMediaAccount(); media.setId(8L); media.setStatus(1);
        when(userMapper.findById(7L)).thenReturn(user);
        when(mediaMapper.findOwnedById(8L, 7L)).thenReturn(media);
        when(dramaMapper.findById(23L)).thenReturn(publishedDrama());
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK)).thenReturn(runtime());
        when(ruleService.findDefaultRule(1L)).thenReturn(new ProviderCommissionRule());
        when(filingMapper.findByConnectionAndMedia(3L, 8L)).thenReturn(null);

        assertThatThrownBy(() -> service.createOrRetry(7L, request()))
                .hasMessageContaining("尚未在该平台加白");
        verifyNoInteractions(adapter);
    }

    private CreatePromotionLinkDTO request() {
        var request = new CreatePromotionLinkDTO(); request.setProviderId(1L); request.setDramaId(23L); request.setMediaAccountId(8L);
        request.setRequestKey("123e4567-e89b-12d3-a456-426614174000"); request.setLandingType("DEFAULT"); return request;
    }
    private ProviderDrama publishedDrama() { var d = new ProviderDrama(); d.setId(23L); d.setConnectionId(3L); d.setExternalDramaId("book-23"); d.setLocalStatus(DramaLocalStatus.PUBLISHED); d.setRemoteShowStatus("1"); return d; }
    private ProviderRuntimeConnection runtime() { return new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", new ProviderConnectionSecret("url", "pid", "key", "USD"), adapter); }
}
