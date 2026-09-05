package com.kasi.backend.provider.spi;

import java.time.LocalDate;

public record AnalyticalReportRequest(LocalDate startDate, LocalDate endDate, int pageNo, int pageSize,
                                      String code, String bookId, String customParams) {
}
