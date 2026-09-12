package com.kasi.backend.promotion.service;

import com.kasi.backend.BaseAuthTest;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("媒体账号报白 XLSX 导出")
class MediaAccountFilingExportTest extends BaseAuthTest {
    private static final List<String> HEADERS = List.of(
            "创建时间", "昵称", "姓名", "电话", "微信号", "媒体平台",
            "账号 ID", "账号名称", "账号链接", "报备状态", "短剧平台");

    @Test
    @DisplayName("导出固定十一列并按同一报白记录筛选且保留管理员真实状态")
    void exportsExactColumnsAndSameFilingFilters() throws Exception {
        jdbcTemplate.update("UPDATE promotion_user SET real_name = '张三', mobile = '13812345678', "
                + "wechat_id = 'wx-stage5' WHERE user_no = ?", PRIMARY_USER_NO);
        long goodShortProviderId = providerId("GOODSHORT");
        long goodShortConnectionId = insertConnection(goodShortProviderId, "GoodShort Export");
        jdbcTemplate.update("INSERT INTO short_drama_provider (provider_code, provider_name, status) "
                + "VALUES ('SECOND_EXPORT', '第二平台', 1)");
        long secondProviderId = providerId("SECOND_EXPORT");
        long secondConnectionId = insertConnection(secondProviderId, "Second Export");

        long accountId = insertAccount("7580215976701887501", "=formula-account");
        insertFiling(goodShortConnectionId, accountId, "API", "SUBMIT_FAILED");
        insertFiling(secondConnectionId, accountId, "MANUAL", "REJECTED");
        insertAccount("no-filing-account", "无报白账号");

        Sheet matched = exportSheet(
                "providerId", String.valueOf(goodShortProviderId),
                "filingMethod", "API",
                "filingStatus", "SUBMIT_FAILED",
                "page", "9",
                "size", "1");
        assertThat(matched.getPhysicalNumberOfRows()).isEqualTo(2);
        assertThat(rowValues(matched.getRow(0))).containsExactlyElementsOf(HEADERS);
        Row row = matched.getRow(1);
        assertThat(row.getLastCellNum()).isEqualTo((short) 11);
        assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(DateUtil.isCellDateFormatted(row.getCell(0))).isTrue();
        assertThat(row.getCell(1).getStringCellValue()).isEqualTo("测试用户");
        assertThat(row.getCell(2).getStringCellValue()).isEqualTo("张三");
        assertThat(row.getCell(3).getStringCellValue()).isEqualTo("13812345678");
        assertThat(row.getCell(4).getStringCellValue()).isEqualTo("wx-stage5");
        assertThat(row.getCell(5).getStringCellValue()).isEqualTo("TikTok");
        assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.STRING);
        assertThat(row.getCell(6).getStringCellValue()).isEqualTo("7580215976701887501");
        assertThat(row.getCell(7).getCellType()).isEqualTo(CellType.STRING);
        assertThat(row.getCell(7).getStringCellValue()).isEqualTo("=formula-account");
        assertThat(row.getCell(9).getStringCellValue()).isEqualTo("提交失败");
        assertThat(row.getCell(10).getStringCellValue()).isEqualTo("GoodShort");

        Sheet impossibleCombination = exportSheet(
                "providerId", String.valueOf(goodShortProviderId),
                "filingMethod", "MANUAL",
                "filingStatus", "SUBMIT_FAILED");
        assertThat(impossibleCombination.getPhysicalNumberOfRows()).isEqualTo(1);

        Sheet noFiling = exportSheet("filingStatus", "NOT_SUBMITTED");
        assertThat(noFiling.getPhysicalNumberOfRows()).isEqualTo(2);
        assertThat(noFiling.getRow(1).getCell(6).getStringCellValue()).isEqualTo("no-filing-account");
        assertThat(noFiling.getRow(1).getCell(9).getStringCellValue()).isEqualTo("待提交");
        assertThat(noFiling.getRow(1).getCell(10).getStringCellValue()).isEmpty();
    }

    @Test
    @DisplayName("导出超过一万行时不受分页参数影响且不截断")
    void exportsEveryMatchingRowBeyondTenThousand() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO promotion_media_account
                    (user_id, media_type, external_account_id, account_name, account_link, status, data_version)
                SELECT (SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK',
                       CONCAT('bulk-', X), CONCAT('账号-', X), NULL, 1, 1
                FROM SYSTEM_RANGE(1, 10001)
                """, PRIMARY_USER_NO);

        Sheet sheet = exportSheet("page", "3", "size", "1");

        assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(10_002);
        assertThat(sheet.getRow(10_001).getCell(6).getStringCellValue()).startsWith("bulk-");
    }

    private Sheet exportSheet(String... params) throws Exception {
        var request = get("/api/admin/promotion/media-accounts/export.xlsx")
                .header("Authorization", "Bearer " + loginAsAdmin("operator", ADMIN_PASSWORD));
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isOne();
            return workbook.cloneSheet(0);
        }
    }

    private long providerId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = ?", Long.class, code);
    }

    private long insertConnection(long providerId, String name) {
        jdbcTemplate.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, currency, status) VALUES (?, ?, 'USD', 1)",
                providerId, name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM short_drama_connection WHERE provider_id = ?", Long.class, providerId);
    }

    private long insertAccount(String externalAccountId, String accountName) {
        jdbcTemplate.update("INSERT INTO promotion_media_account "
                        + "(user_id, media_type, external_account_id, account_name, account_link, status, data_version) "
                        + "VALUES ((SELECT id FROM promotion_user WHERE user_no = ?), 'TIKTOK', ?, ?, ?, 1, 1)",
                PRIMARY_USER_NO, externalAccountId, accountName,
                "https://tiktok.com/@" + externalAccountId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM promotion_media_account WHERE external_account_id = ?",
                Long.class, externalAccountId);
    }

    private void insertFiling(long connectionId, long accountId, String method, String filingStatus) {
        jdbcTemplate.update("INSERT INTO provider_media_filing "
                        + "(connection_id, media_account_id, filing_method, status, task_data_version, next_action) "
                        + "VALUES (?, ?, ?, ?, 1, 'NONE')",
                connectionId, accountId, method, filingStatus);
    }

    private List<String> rowValues(Row row) {
        return java.util.stream.IntStream.range(0, row.getLastCellNum())
                .mapToObj(index -> row.getCell(index).getStringCellValue()).toList();
    }
}
