package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.dto.PromotionOrderPageQueryDTO;
import com.kasi.backend.promotion.dto.PromotionOrderSyncDTO;
import com.kasi.backend.promotion.entity.PromotionOrder;
import com.kasi.backend.promotion.enums.PromotionOrderStatus;
import com.kasi.backend.promotion.enums.PromotionAttributionStatus;
import com.kasi.backend.promotion.enums.PromotionCommissionStatus;
import com.kasi.backend.promotion.mapper.PromotionOrderMapper;
import com.kasi.backend.promotion.service.impl.PromotionOrderAdminServiceImpl;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEFAULTS;

class PromotionOrderAdminServiceTest {
    private static final List<String> EXPORT_HEADERS = List.of(
            "订单ID", "平台ID", "订单金额", "币种", "状态", "支付时间",
            "用户ID", "归因状态", "佣金", "佣金状态");

    @Test
    @DisplayName("管理员手动同步委托共享同步服务并保留指定时间窗")
    void syncDelegatesToSharedService() {
        PromotionOrderSyncService syncService = mock(PromotionOrderSyncService.class);
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(syncService, null);
        PromotionOrderSyncDTO request = new PromotionOrderSyncDTO();
        request.setProviderId(7L);
        request.setStartDate(LocalDateTime.of(2025, 7, 1, 0, 0));
        request.setEndDate(LocalDateTime.of(2025, 7, 1, 23, 59, 59));
        var expected = com.kasi.backend.promotion.vo.PromotionOrderSyncResultVO.builder()
                .fetchedCount(3).insertedCount(2).updatedCount(1).unattributedCount(1).build();
        when(syncService.sync(request.getProviderId(), request.getStartDate(), request.getEndDate()))
                .thenReturn(expected);

        var result = service.sync(request);

        assertThat(result).isSameAs(expected);
        verify(syncService).sync(7L, request.getStartDate(), request.getEndDate());
    }

    @Test
    @DisplayName("管理端订单返回真实追踪号且退款当前有效佣金为零")
    void adminOrderShowsTrackingAndZeroEffectiveRefundCommission() {
        PromotionOrderMapper mapper = mock(PromotionOrderMapper.class);
        PromotionOrder order = new PromotionOrder();
        order.setId(9L);
        order.setTrackingNo("tracking-9");
        order.setStatus(PromotionOrderStatus.REFUNDED);
        order.setCommissionAmount(new BigDecimal("4.79"));
        when(mapper.findPage(null, null, null, null, null, null, 0, 20, false))
                .thenReturn(List.of(order));
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(null, mapper);
        PromotionOrderPageQueryDTO query = new PromotionOrderPageQueryDTO();

        var result = service.getPage(query);

        assertThat(result.getList().getFirst().getTrackingNo()).isEqualTo("tracking-9");
        assertThat(result.getList().getFirst().getCommissionAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("订单 XLSX 保留十列和金额口径并导出全部筛选结果")
    void exportsAllFilteredOrdersAsRealXlsx() throws Exception {
        PromotionOrder order = new PromotionOrder();
        order.setExternalOrderId("7580215976701887501");
        order.setProviderId(7L);
        order.setOrderAmount(new BigDecimal("19.98"));
        order.setCurrency("USD");
        order.setStatus(PromotionOrderStatus.REFUNDED);
        order.setPaidAt(LocalDateTime.of(2026, 9, 10, 12, 30));
        order.setUserId(7580215976701887501L);
        order.setAttributionStatus(PromotionAttributionStatus.ATTRIBUTED);
        order.setCommissionAmount(new BigDecimal("4.79"));
        order.setCommissionStatus(PromotionCommissionStatus.REVERSED);
        List<PromotionOrder> orders = Collections.nCopies(10_001, order);
        AtomicReference<Object[]> exportArguments = new AtomicReference<>();
        PromotionOrderMapper mapper = mock(PromotionOrderMapper.class, invocation -> {
            if (invocation.getMethod().getName().equals("findForExport")) {
                exportArguments.set(invocation.getArguments());
                return orders;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        PromotionOrderAdminServiceImpl service = new PromotionOrderAdminServiceImpl(null, mapper);
        PromotionOrderPageQueryDTO query = new PromotionOrderPageQueryDTO();
        query.setPage(9);
        query.setSize(1);
        query.setProviderId(7L);
        query.setStatus(PromotionOrderStatus.REFUNDED);
        query.setAttributionStatus(PromotionAttributionStatus.ATTRIBUTED);
        query.setStartDate(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndDate(LocalDateTime.of(2026, 10, 1, 0, 0));

        byte[] bytes = service.exportXlsx(query);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isOne();
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(10_002);
            assertThat(java.util.stream.IntStream.range(0, sheet.getRow(0).getLastCellNum())
                    .mapToObj(index -> sheet.getRow(0).getCell(index).getStringCellValue()).toList())
                    .containsExactlyElementsOf(EXPORT_HEADERS);
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("7580215976701887501");
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(19.98);
            assertThat(DateUtil.isCellDateFormatted(row.getCell(5))).isTrue();
            assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("7580215976701887501");
            assertThat(row.getCell(8).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(8).getNumericCellValue()).isZero();
        }
        assertThat(exportArguments.get()).containsExactly(
                7L, null, PromotionOrderStatus.REFUNDED, PromotionAttributionStatus.ATTRIBUTED,
                query.getStartDate(), query.getEndDate(), false);
    }
}
