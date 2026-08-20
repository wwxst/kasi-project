package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.promotion.dto.CreateMediaAccountDTO;
import com.kasi.backend.promotion.dto.AdminUpdateMediaAccountDTO;
import com.kasi.backend.promotion.dto.UpdateMediaAccountDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.entity.ShortDramaProvider;
import com.kasi.backend.provider.enums.FilingMode;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.AccountFilingProviderAdapter;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("媒体账号业务")
class MediaAccountServiceTest {
    private PromotionMediaAccountMapper mediaMapper;
    private ProviderMediaFilingMapper filingMapper;
    private ProviderRuntimeConnectionService runtimeService;
    private AccountFilingProviderAdapter adapter;
    private com.kasi.backend.provider.mapper.ShortDramaConnectionMapper connectionMapper;
    private com.kasi.backend.provider.mapper.ShortDramaProviderMapper providerMapper;
    private MediaAccountService service;

    @BeforeEach
    void setUp() {
        mediaMapper = mock(PromotionMediaAccountMapper.class);
        filingMapper = mock(ProviderMediaFilingMapper.class);
        runtimeService = mock(ProviderRuntimeConnectionService.class);
        adapter = mock(AccountFilingProviderAdapter.class);
        connectionMapper = mock(com.kasi.backend.provider.mapper.ShortDramaConnectionMapper.class);
        providerMapper = mock(com.kasi.backend.provider.mapper.ShortDramaProviderMapper.class);
        when(adapter.supportedMediaTypes()).thenReturn(Set.of(MediaType.TIKTOK));
        service = new com.kasi.backend.promotion.service.impl.MediaAccountServiceImpl(
                mediaMapper, filingMapper, runtimeService, connectionMapper, providerMapper);
    }

    @Test
    @DisplayName("创建媒体账号同时建立审核中的首个平台报备")
    void createCreatesPendingFiling() {
        when(runtimeService.resolveAll(com.kasi.backend.provider.enums.ProviderCapability.ACCOUNT_FILING))
                .thenReturn(List.of(runtime(adapter, 21L), runtime(adapter, 22L)));
        when(mediaMapper.findByIdentity(MediaType.TIKTOK, "creator-1")).thenReturn(null);
        when(mediaMapper.insert(any())).thenAnswer(invocation -> {
            PromotionMediaAccount account = invocation.getArgument(0);
            account.setId(31L);
            return 1;
        });
        when(filingMapper.insert(any())).thenAnswer(invocation -> {
            ProviderMediaFiling filing = invocation.getArgument(0);
            filing.setId(41L);
            return 1;
        });
        when(mediaMapper.findOwnedById(31L, 1L)).thenReturn(account(31L, 1L, MediaType.TIKTOK, "creator-1", 1));
        when(filingMapper.findByMediaAccountId(31L)).thenReturn(List.of(
                filing(41L, 21L, 31L, FilingStatus.PENDING, 1),
                filing(42L, 22L, 31L, FilingStatus.PENDING, 1)));

        CreateMediaAccountDTO request = new CreateMediaAccountDTO();
        request.setMediaType(MediaType.TIKTOK);
        request.setExternalAccountId(" creator-1 ");
        request.setAccountName(" Creator ");
        request.setAccountLink("https://tiktok.com/@creator-1");

        var result = service.create(1L, request);

        assertThat(result.getExternalAccountId()).isEqualTo("creator-1");
        verify(filingMapper, times(2)).insert(argThat(f -> f.getStatus() == FilingStatus.PENDING));
    }

    @Test
    @DisplayName("人工报白创建账号时不排入API任务")
    void manualModeCreatesNoApiTask() {
        when(runtimeService.resolveAll(com.kasi.backend.provider.enums.ProviderCapability.ACCOUNT_FILING))
                .thenReturn(List.of(runtime(adapter, 21L)));
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(21L);
        connection.setProviderId(10L);
        connection.setFilingMode(FilingMode.MANUAL);
        when(connectionMapper.findById(21L)).thenReturn(connection);
        when(mediaMapper.findByIdentity(MediaType.TIKTOK, "creator-manual")).thenReturn(null);
        when(mediaMapper.insert(any())).thenAnswer(invocation -> { ((PromotionMediaAccount) invocation.getArgument(0)).setId(31L); return 1; });
        when(filingMapper.insert(any())).thenAnswer(invocation -> { ((ProviderMediaFiling) invocation.getArgument(0)).setId(41L); return 1; });
        when(mediaMapper.findOwnedById(31L, 1L)).thenReturn(account(31L, 1L, MediaType.TIKTOK, "creator-manual", 1));
        when(filingMapper.findByMediaAccountId(31L)).thenReturn(List.of(filing(41L, 21L, 31L, FilingStatus.PENDING, 1)));

        CreateMediaAccountDTO request = new CreateMediaAccountDTO();
        request.setMediaType(MediaType.TIKTOK);
        request.setExternalAccountId("creator-manual");

        service.create(1L, request);

        verify(filingMapper).insert(argThat(f -> f.getNextAction() == com.kasi.backend.promotion.enums.FilingAction.NONE));
    }

    @Test
    @DisplayName("已加白后不能修改媒体平台和账号ID")
    void approvedIdentityCannotChange() {
        PromotionMediaAccount existing = account(31L, 1L, MediaType.TIKTOK, "creator-1", 1);
        when(mediaMapper.findByIdForUpdate(31L)).thenReturn(existing);
        when(filingMapper.findByMediaAccountId(31L)).thenReturn(
                List.of(filing(41L, 21L, 31L, FilingStatus.APPROVED, 1)));
        UpdateMediaAccountDTO request = new UpdateMediaAccountDTO();
        request.setMediaType(MediaType.TIKTOK);
        request.setExternalAccountId("creator-2");

        assertThatThrownBy(() -> service.update(1L, 31L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(7003));
        verify(mediaMapper, never()).updateDetails(any());
    }

    @Test
    @DisplayName("管理员不能修改已加白账号的平台和账号ID")
    void adminCannotChangeApprovedIdentity() {
        PromotionMediaAccount existing = account(31L, 1L, MediaType.TIKTOK, "creator-1", 1);
        when(mediaMapper.findByIdForUpdate(31L)).thenReturn(existing);
        when(filingMapper.findByMediaAccountId(31L)).thenReturn(
                List.of(filing(41L, 21L, 31L, FilingStatus.APPROVED, 1)));

        AdminUpdateMediaAccountDTO request = new AdminUpdateMediaAccountDTO();
        request.setMediaType(MediaType.TIKTOK);
        request.setExternalAccountId("creator-2");
        request.setStatus(1);

        assertThatThrownBy(() -> service.updateByAdmin(31L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(7003));
        verify(mediaMapper, never()).updateDetails(any());
        verify(mediaMapper, never()).updateStatus(any(), any());
    }

    @Test
    @DisplayName("管理员可以在未加白时纠正账号身份并重新排队报备")
    void adminCanChangePendingIdentity() {
        PromotionMediaAccount existing = account(31L, 1L, MediaType.TIKTOK, "creator-1", 1);
        when(mediaMapper.findByIdForUpdate(31L)).thenReturn(existing);
        when(mediaMapper.findByIdentity(MediaType.TIKTOK, "creator-2")).thenReturn(null);
        when(filingMapper.findByMediaAccountId(31L)).thenReturn(
                List.of(filing(41L, 21L, 31L, FilingStatus.PENDING, 1)), List.of());
        when(mediaMapper.updateStatus(31L, 1)).thenReturn(1);
        when(mediaMapper.findById(31L)).thenReturn(existing);

        AdminUpdateMediaAccountDTO request = new AdminUpdateMediaAccountDTO();
        request.setMediaType(MediaType.TIKTOK);
        request.setExternalAccountId("creator-2");
        request.setAccountName("Creator 2");
        request.setAccountLink("https://tiktok.com/@creator-2");
        request.setStatus(1);

        service.updateByAdmin(31L, request);

        verify(mediaMapper).updateDetails(argThat(account ->
                account.getDataVersion() == 2 && account.getExternalAccountId().equals("creator-2")));
        verify(filingMapper).reschedule(eq(41L), eq(FilingStatus.PENDING),
                eq(com.kasi.backend.promotion.enums.FilingAction.SUBMIT), eq(1), eq(2), any(LocalDateTime.class));
    }

    private ProviderRuntimeConnection runtime(AccountFilingProviderAdapter adapter, Long connectionId) {
        ShortDramaProvider provider = new ShortDramaProvider();
        provider.setId(10L);
        provider.setProviderCode("GOODSHORT");
        provider.setProviderName("GoodShort");
        provider.setStatus(1);
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(connectionId);
        connection.setProviderId(10L);
        return new ProviderRuntimeConnection(connectionId, 10L, "GOODSHORT", "GoodShort",
                new com.kasi.backend.provider.spi.ProviderConnectionSecret("https://test", "pid", "key", "USD"), adapter);
    }

    private PromotionMediaAccount account(Long id, Long userId, MediaType type, String externalId, int version) {
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setId(id);
        account.setUserId(userId);
        account.setMediaType(type);
        account.setExternalAccountId(externalId);
        account.setAccountName("Creator");
        account.setAccountLink("https://tiktok.com/@" + externalId);
        account.setStatus(1);
        account.setDataVersion(version);
        return account;
    }

    private ProviderMediaFiling filing(Long id, Long connectionId, Long mediaId, FilingStatus status, int version) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setId(id);
        filing.setConnectionId(connectionId);
        filing.setMediaAccountId(mediaId);
        filing.setStatus(status);
        filing.setTaskDataVersion(version);
        filing.setNextAction(com.kasi.backend.promotion.enums.FilingAction.SUBMIT);
        filing.setNextActionAt(LocalDateTime.now());
        return filing;
    }
}
