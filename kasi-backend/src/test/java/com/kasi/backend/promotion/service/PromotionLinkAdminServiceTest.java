package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.AdminPromotionLinkPageQueryDTO;
import com.kasi.backend.promotion.mapper.PromotionLinkMapper;
import com.kasi.backend.promotion.service.impl.PromotionLinkAdminServiceImpl;
import com.kasi.backend.promotion.vo.AdminPromotionLinkVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionLinkAdminServiceTest {
    @Test
    @DisplayName("管理员推广任务分页转交用户编号、平台、口令和追踪号筛选")
    void pageDelegatesTaskFiltersToMapper() {
        PromotionLinkMapper mapper = mock(PromotionLinkMapper.class);
        AdminPromotionLinkPageQueryDTO query = new AdminPromotionLinkPageQueryDTO();
        query.setPage(2);
        query.setSize(10);
        query.setUserNo("583104726918");
        query.setProviderId(7L);
        query.setExternalCode("code-7");
        query.setTrackingNo("tracking-7");
        when(mapper.countAdminPage("583104726918", 7L, "code-7", "tracking-7")).thenReturn(1L);
        when(mapper.findAdminPage("583104726918", 7L, "code-7", "tracking-7", 10, 10))
                .thenReturn(List.of(AdminPromotionLinkVO.builder().id(7L).build()));

        var result = new PromotionLinkAdminServiceImpl(mapper).getPage(query);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).extracting(AdminPromotionLinkVO::getId).containsExactly(7L);
        verify(mapper).countAdminPage("583104726918", 7L, "code-7", "tracking-7");
        verify(mapper).findAdminPage("583104726918", 7L, "code-7", "tracking-7", 10, 10);
    }
}
