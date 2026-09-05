package com.kasi.backend.provider.spi;

import java.util.List;

public record ProviderAnalyticalReportPage(List<ProviderAnalyticalReportRecord> records,
                                           int pageNo, int pageSize, int pages, long total, boolean hasNext) {
}
