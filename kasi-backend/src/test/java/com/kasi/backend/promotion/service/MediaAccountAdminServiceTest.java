package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.promotion.dto.UpdateManualFilingStatusDTO;
import com.kasi.backend.promotion.entity.PromotionMediaAccount;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.SubmissionResolution;
import com.kasi.backend.promotion.mapper.PromotionMediaAccountMapper;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.mapper.MediaAccountFilingExportMapper;
import com.kasi.backend.promotion.service.impl.MediaAccountAdminServiceImpl;
import com.kasi.backend.provider.entity.ShortDramaConnection;
import com.kasi.backend.provider.mapper.ShortDramaConnectionMapper;
import com.kasi.backend.provider.mapper.ShortDramaProviderMapper;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("管理员报白处理服务")
class MediaAccountAdminServiceTest {
    private PromotionMediaAccountMapper mediaMapper;
    private ProviderMediaFilingMapper filingMapper;
    private ShortDramaConnectionMapper connectionMapper;
    private MediaAccountAdminService service;

    @BeforeEach
    void setUp() {
        mediaMapper = mock(PromotionMediaAccountMapper.class);
        filingMapper = mock(ProviderMediaFilingMapper.class);
        connectionMapper = mock(ShortDramaConnectionMapper.class);
        service = new MediaAccountAdminServiceImpl(mediaMapper, filingMapper,
                mock(PromotionUserMapper.class), mock(MediaAccountService.class), connectionMapper,
                mock(ShortDramaProviderMapper.class), mock(MediaAccountFilingExportMapper.class));
        when(mediaMapper.findById(31L)).thenReturn(account());
        when(connectionMapper.findByProviderId(10L)).thenReturn(connection());
    }

    @Test
    @DisplayName("人工状态只按确认矩阵更新且无需原因")
    void manualStatusesCanBeUpdated() {
        FilingStatus[][] transitions = {
                {FilingStatus.NOT_SUBMITTED, FilingStatus.APPROVED},
                {FilingStatus.NOT_SUBMITTED, FilingStatus.REJECTED},
                {FilingStatus.PENDING, FilingStatus.APPROVED},
                {FilingStatus.PENDING, FilingStatus.REJECTED},
                {FilingStatus.APPROVED, FilingStatus.REJECTED},
                {FilingStatus.REJECTED, FilingStatus.APPROVED},
                {FilingStatus.SUBMIT_FAILED, FilingStatus.APPROVED},
                {FilingStatus.SUBMIT_FAILED, FilingStatus.REJECTED}
        };
        for (FilingStatus[] transition : transitions) {
            FilingStatus source = transition[0];
            FilingStatus target = transition[1];
            reset(filingMapper);
            ProviderMediaFiling filing = filing(FilingMethod.MANUAL, source);
            when(filingMapper.findByConnectionAndMedia(21L, 31L)).thenReturn(filing);
            ProviderMediaFiling updated = filing(FilingMethod.MANUAL, target);
            when(filingMapper.findById(41L)).thenReturn(updated);
            when(filingMapper.updateManualStatus(eq(41L), eq(source), eq(target), eq(1),
                    eq(9L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
            UpdateManualFilingStatusDTO request = new UpdateManualFilingStatusDTO();
            request.setStatus(target);

            service.updateManualStatus(9L, 31L, 10L, request);

            verify(filingMapper).updateManualStatus(eq(41L), eq(source), eq(target), eq(1),
                    eq(9L), any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }

    @Test
    @DisplayName("人工记录不再接受标记已提交为审核中")
    void manualSubmissionMarkerIsRejected() {
        when(filingMapper.findByConnectionAndMedia(21L, 31L))
                .thenReturn(filing(FilingMethod.MANUAL, FilingStatus.NOT_SUBMITTED));
        UpdateManualFilingStatusDTO request = new UpdateManualFilingStatusDTO();
        request.setStatus(FilingStatus.PENDING);

        assertThatThrownBy(() -> service.updateManualStatus(9L, 31L, 10L, request))
                .isInstanceOf(BusinessException.class);
        verify(filingMapper, never()).updateManualStatus(anyLong(), any(), any(), anyInt(),
                anyLong(), any(), any());
    }

    @Test
    @DisplayName("人工终态不能退回审核中或重复写入同一状态")
    void invalidManualTransitionsAreRejected() {
        FilingStatus[][] transitions = {
                {FilingStatus.APPROVED, FilingStatus.PENDING},
                {FilingStatus.REJECTED, FilingStatus.PENDING},
                {FilingStatus.PENDING, FilingStatus.PENDING},
                {FilingStatus.APPROVED, FilingStatus.APPROVED},
                {FilingStatus.REJECTED, FilingStatus.REJECTED}
        };
        for (FilingStatus[] transition : transitions) {
            reset(filingMapper);
            when(filingMapper.findByConnectionAndMedia(21L, 31L))
                    .thenReturn(filing(FilingMethod.MANUAL, transition[0]));
            UpdateManualFilingStatusDTO request = new UpdateManualFilingStatusDTO();
            request.setStatus(transition[1]);

            assertThatThrownBy(() -> service.updateManualStatus(9L, 31L, 10L, request))
                    .isInstanceOf(BusinessException.class);
            verify(filingMapper, never()).updateManualStatus(anyLong(), any(), any(), anyInt(),
                    anyLong(), any(), any());
        }
    }

    @Test
    @DisplayName("API报白拒绝人工状态操作且并发更新失败")
    void apiAndConcurrentUpdatesAreRejected() {
        ProviderMediaFiling api = filing(FilingMethod.API, FilingStatus.PENDING);
        when(filingMapper.findByConnectionAndMedia(21L, 31L)).thenReturn(api);
        UpdateManualFilingStatusDTO request = new UpdateManualFilingStatusDTO();
        request.setStatus(FilingStatus.APPROVED);
        assertThatThrownBy(() -> service.updateManualStatus(9L, 31L, 10L, request))
                .isInstanceOf(BusinessException.class);
        verify(filingMapper, never()).updateManualStatus(anyLong(), any(), any(), anyInt(), anyLong(), any(), any());

        reset(filingMapper);
        ProviderMediaFiling manual = filing(FilingMethod.MANUAL, FilingStatus.PENDING);
        when(filingMapper.findByConnectionAndMedia(21L, 31L)).thenReturn(manual);
        when(filingMapper.updateManualStatus(anyLong(), any(), any(), anyInt(), anyLong(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.updateManualStatus(9L, 31L, 10L, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("未知提交仅可核实为已收到或未收到并受版本租约保护")
    void unknownSubmissionCanBeResolved() {
        ProviderMediaFiling unknown = filing(FilingMethod.API, FilingStatus.SUBMIT_FAILED);
        unknown.setLastErrorCode("SUBMIT_OUTCOME_UNKNOWN");
        when(filingMapper.findByConnectionAndMedia(21L, 31L)).thenReturn(unknown, unknown);
        when(filingMapper.findById(41L)).thenReturn(filing(FilingMethod.API, FilingStatus.PENDING));
        when(filingMapper.resolveUnknownSubmission(eq(41L), eq(SubmissionResolution.RECEIVED), eq(1),
                eq(9L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        service.resolveSubmission(9L, 31L, 10L, SubmissionResolution.RECEIVED);

        verify(filingMapper).resolveUnknownSubmission(eq(41L), eq(SubmissionResolution.RECEIVED), eq(1),
                eq(9L), any(LocalDateTime.class), any(LocalDateTime.class));

        unknown.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        when(filingMapper.findByConnectionAndMedia(21L, 31L)).thenReturn(unknown);
        assertThatThrownBy(() -> service.resolveSubmission(9L, 31L, 10L, SubmissionResolution.NOT_RECEIVED))
                .isInstanceOf(BusinessException.class);
    }

    private PromotionMediaAccount account() {
        PromotionMediaAccount account = new PromotionMediaAccount();
        account.setId(31L);
        return account;
    }

    private ShortDramaConnection connection() {
        ShortDramaConnection connection = new ShortDramaConnection();
        connection.setId(21L);
        connection.setProviderId(10L);
        return connection;
    }

    private ProviderMediaFiling filing(FilingMethod method, FilingStatus status) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setId(41L);
        filing.setConnectionId(21L);
        filing.setMediaAccountId(31L);
        filing.setFilingMethod(method);
        filing.setStatus(status);
        filing.setTaskDataVersion(1);
        filing.setNextAction(FilingAction.NONE);
        return filing;
    }
}
