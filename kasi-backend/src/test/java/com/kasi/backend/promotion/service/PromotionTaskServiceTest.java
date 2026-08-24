package com.kasi.backend.promotion.service;

import com.kasi.backend.drama.entity.ProviderDrama;
import com.kasi.backend.drama.enums.DramaLocalStatus;
import com.kasi.backend.drama.mapper.ProviderDramaMapper;
import com.kasi.backend.promotion.dto.CreatePromotionTaskDTO;
import com.kasi.backend.promotion.entity.PromotionTask;
import com.kasi.backend.promotion.enums.PromotionTaskStatus;
import com.kasi.backend.promotion.mapper.PromotionTaskMapper;
import com.kasi.backend.promotion.service.impl.PromotionTaskServiceImpl;
import com.kasi.backend.provider.enums.ProviderCapability;
import com.kasi.backend.provider.service.ProviderRuntimeConnectionService;
import com.kasi.backend.provider.spi.ProviderAdapter;
import com.kasi.backend.provider.spi.ProviderRuntimeConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionTaskServiceTest {
    @Mock PromotionTaskMapper mapper;
    @Mock ProviderDramaMapper dramaMapper;
    @Mock ProviderRuntimeConnectionService runtimeService;
    @Mock ProviderAdapter adapter;
    @InjectMocks PromotionTaskServiceImpl service;

    @Test
    @DisplayName("多选推广平台会拆分为多条推广任务并保持幂等")
    void createsOneTaskPerMediaType() {
        ProviderDrama drama = new ProviderDrama();
        drama.setId(23L); drama.setProviderId(1L); drama.setConnectionId(3L);
        drama.setLocalStatus(DramaLocalStatus.PUBLISHED); drama.setRemoteShowStatus("1");
        when(dramaMapper.findById(23L)).thenReturn(drama);
        when(runtimeService.resolve(1L, ProviderCapability.PROMOTION_LINK))
                .thenReturn(new ProviderRuntimeConnection(3L, 1L, "GOODSHORT", "GoodShort", null, adapter));
        when(mapper.findByRequestAndMedia(7L, "request-1", "TIKTOK")).thenReturn(null);
        when(mapper.findByRequestAndMedia(7L, "request-1", "FACEBOOK")).thenReturn(null);
        doAnswer(invocation -> { invocation.getArgument(0, PromotionTask.class).setId(1L); return 1; }).when(mapper).insert(any());
        when(mapper.pageByUser(eq(7L), any(), any(), any(), eq(0), eq(20))).thenReturn(java.util.List.of());

        CreatePromotionTaskDTO request = new CreatePromotionTaskDTO();
        request.setProviderId(1L); request.setDramaId(23L); request.setTaskName("夏季推广");
        request.setRequestKey("request-1"); request.setMediaTypes(java.util.List.of("TIKTOK", "FACEBOOK"));

        service.create(7L, request);

        verify(mapper, times(2)).insert(any(PromotionTask.class));
        verify(mapper).findByRequestAndMedia(7L, "request-1", "TIKTOK");
        verify(mapper).findByRequestAndMedia(7L, "request-1", "FACEBOOK");
    }

    @Test
    @DisplayName("推广任务列表返回真实统计字段")
    void listsTasksWithStatistics() {
        PromotionTask task = new PromotionTask();
        task.setId(1L); task.setTaskName("任务"); task.setStatus(PromotionTaskStatus.PENDING);
        task.setCodeSearchCount(2); task.setDirectClickCount(3); task.setOrderCount(0);
        when(mapper.pageByUser(7L, null, null, null, 0, 20)).thenReturn(java.util.List.of(task));
        when(mapper.countByUser(7L, null, null, null)).thenReturn(1L);

        var page = service.getMine(7L, new com.kasi.backend.promotion.dto.PromotionTaskPageQueryDTO());

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList().get(0).getDirectClickCount()).isEqualTo(3L);
    }
}
