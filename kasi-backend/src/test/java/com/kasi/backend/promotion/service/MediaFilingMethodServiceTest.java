package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.entity.ProviderMediaFiling;
import com.kasi.backend.promotion.enums.FilingAction;
import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.FilingStatus;
import com.kasi.backend.promotion.enums.MediaType;
import com.kasi.backend.promotion.mapper.ProviderMediaFilingMapper;
import com.kasi.backend.promotion.service.impl.MediaFilingMethodServiceImpl;
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

@DisplayName("报白方式切换服务")
class MediaFilingMethodServiceTest {
    private ProviderMediaFilingMapper filingMapper;
    private MediaFilingMethodService service;

    @BeforeEach
    void setUp() {
        filingMapper = mock(ProviderMediaFilingMapper.class);
        service = new MediaFilingMethodServiceImpl(filingMapper);
    }

    @Test
    @DisplayName("人工转API按五状态安排任务且API转人工全部停止")
    void switchesMethodsUsingPersistedStatus() {
        ProviderMediaFiling notSubmitted = filing(1L, FilingMethod.MANUAL, FilingStatus.NOT_SUBMITTED, 1);
        ProviderMediaFiling pending = filing(2L, FilingMethod.MANUAL, FilingStatus.PENDING, 2);
        ProviderMediaFiling approved = filing(3L, FilingMethod.MANUAL, FilingStatus.APPROVED, 3);
        ProviderMediaFiling rejected = filing(4L, FilingMethod.MANUAL, FilingStatus.REJECTED, 4);
        ProviderMediaFiling failed = filing(5L, FilingMethod.MANUAL, FilingStatus.SUBMIT_FAILED, 5);
        ProviderMediaFiling toManual = filing(6L, FilingMethod.API, FilingStatus.PENDING, 6);
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.TIKTOK))
                .thenReturn(List.of(notSubmitted, pending, approved, rejected, failed));
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.FACEBOOK))
                .thenReturn(List.of(toManual));
        when(filingMapper.switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(1);

        service.switchMethods(10L, Set.of(MediaType.TIKTOK), Set.of(MediaType.FACEBOOK));

        verifySwitch(1L, FilingMethod.API, FilingStatus.NOT_SUBMITTED, FilingAction.SUBMIT, 1);
        verifySwitch(2L, FilingMethod.API, FilingStatus.PENDING, FilingAction.QUERY, 2);
        verifySwitch(3L, FilingMethod.API, FilingStatus.APPROVED, FilingAction.NONE, 3);
        verifySwitch(4L, FilingMethod.API, FilingStatus.REJECTED, FilingAction.NONE, 4);
        verifySwitch(5L, FilingMethod.API, FilingStatus.SUBMIT_FAILED, FilingAction.NONE, 5);
        verifySwitch(6L, FilingMethod.MANUAL, FilingStatus.PENDING, FilingAction.NONE, 6);
    }

    @Test
    @DisplayName("重复保存不修改任务版本")
    void unchangedConfigurationDoesNothing() {
        service.switchMethods(10L, Set.of(), Set.of());
        verifyNoInteractions(filingMapper);
    }

    @Test
    @DisplayName("活动租约或未核实提交结果阻止整批切换")
    void activeLeaseOrUnknownSubmissionBlocksWholeSwitch() {
        ProviderMediaFiling leased = filing(1L, FilingMethod.MANUAL, FilingStatus.PENDING, 1);
        leased.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.TIKTOK))
                .thenReturn(List.of(leased));

        assertThatThrownBy(() -> service.switchMethods(10L, Set.of(MediaType.TIKTOK), Set.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.MEDIA_FILING_METHOD_SWITCH_BLOCKED.getCode()));
        verify(filingMapper, never()).switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any());

        reset(filingMapper);
        ProviderMediaFiling unknown = filing(2L, FilingMethod.MANUAL, FilingStatus.SUBMIT_FAILED, 1);
        unknown.setLastErrorCode("SUBMIT_OUTCOME_UNKNOWN");
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.TIKTOK))
                .thenReturn(List.of(unknown));
        assertThatThrownBy(() -> service.switchMethods(10L, Set.of(MediaType.TIKTOK), Set.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.MEDIA_FILING_METHOD_SWITCH_BLOCKED.getCode()));
        verify(filingMapper, never()).switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("已发起但尚未收敛结果的提交阻止方式切换")
    void unresolvedSubmitAttemptBlocksMethodSwitch() {
        ProviderMediaFiling unresolved = filing(3L, FilingMethod.API, FilingStatus.NOT_SUBMITTED, 2);
        unresolved.setNextAction(FilingAction.SUBMIT);
        unresolved.setLastSubmitAttemptAt(LocalDateTime.now().minusMinutes(2));
        unresolved.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.FACEBOOK))
                .thenReturn(List.of(unresolved));

        assertThatThrownBy(() -> service.switchMethods(10L, Set.of(), Set.of(MediaType.FACEBOOK)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.MEDIA_FILING_METHOD_SWITCH_BLOCKED.getCode()));
        verify(filingMapper, never()).switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("甲方明确拒绝后允许API转人工")
    void remoteRejectedAllowsSwitchToManual() {
        ProviderMediaFiling rejected = filing(4L, FilingMethod.API, FilingStatus.SUBMIT_FAILED, 3);
        rejected.setLastSubmitAttemptAt(LocalDateTime.now().minusMinutes(2));
        rejected.setLastErrorCode("REMOTE_REJECTED");
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.FACEBOOK))
                .thenReturn(List.of(rejected));
        when(filingMapper.switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(1);

        service.switchMethods(10L, Set.of(), Set.of(MediaType.FACEBOOK));

        verifySwitch(4L, FilingMethod.MANUAL, FilingStatus.SUBMIT_FAILED, FilingAction.NONE, 3);
    }

    @Test
    @DisplayName("未知提交已确认收到后允许API转人工")
    void confirmedReceivedAllowsSwitchToManual() {
        ProviderMediaFiling received = filing(5L, FilingMethod.API, FilingStatus.PENDING, 4);
        received.setNextAction(FilingAction.QUERY);
        received.setLastSubmitAttemptAt(LocalDateTime.now().minusMinutes(2));
        received.setLastSubmittedAt(LocalDateTime.now().minusMinutes(1));
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.FACEBOOK))
                .thenReturn(List.of(received));
        when(filingMapper.switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(1);

        service.switchMethods(10L, Set.of(), Set.of(MediaType.FACEBOOK));

        verifySwitch(5L, FilingMethod.MANUAL, FilingStatus.PENDING, FilingAction.NONE, 4);
    }

    @Test
    @DisplayName("未知提交已确认未收到后允许API转人工")
    void confirmedNotReceivedAllowsSwitchToManual() {
        ProviderMediaFiling notReceived = filing(6L, FilingMethod.API, FilingStatus.SUBMIT_FAILED, 5);
        notReceived.setLastSubmitAttemptAt(LocalDateTime.now().minusMinutes(2));
        notReceived.setLastErrorCode("SUBMIT_CONFIRMED_NOT_RECEIVED");
        when(filingMapper.findByConnectionAndMediaTypeForUpdate(10L, MediaType.FACEBOOK))
                .thenReturn(List.of(notReceived));
        when(filingMapper.switchMethodAndSchedule(anyLong(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(1);

        service.switchMethods(10L, Set.of(), Set.of(MediaType.FACEBOOK));

        verifySwitch(6L, FilingMethod.MANUAL, FilingStatus.SUBMIT_FAILED, FilingAction.NONE, 5);
    }

    @Test
    @DisplayName("配置JSON决定新账号的API或人工方式")
    void resolvesMethodFromConfiguration() {
        assertThat(service.resolveMethod("[\"FACEBOOK\",\"TIKTOK\"]", MediaType.TIKTOK))
                .isEqualTo(FilingMethod.API);
        assertThat(service.resolveMethod("[]", MediaType.TIKTOK)).isEqualTo(FilingMethod.MANUAL);
    }

    private void verifySwitch(Long id, FilingMethod method, FilingStatus status, FilingAction action, int version) {
        verify(filingMapper).switchMethodAndSchedule(eq(id), eq(method), eq(status), eq(action),
                action == FilingAction.NONE ? isNull() : any(LocalDateTime.class), eq(version), any(LocalDateTime.class));
    }

    private ProviderMediaFiling filing(Long id, FilingMethod method, FilingStatus status, int version) {
        ProviderMediaFiling filing = new ProviderMediaFiling();
        filing.setId(id);
        filing.setFilingMethod(method);
        filing.setStatus(status);
        filing.setNextAction(FilingAction.NONE);
        filing.setTaskDataVersion(version);
        return filing;
    }
}
