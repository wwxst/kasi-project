package com.kasi.backend.provider.spi;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderAnalyticalReportRecord(LocalDate reportDate, String pid, String customParams, String bookId,
                                             String code, long clickCount, long attributedUserCount,
                                             long newRegisteredUserCount, long newPaidUserCount,
                                             long newMemberUserCount, long paidUserCount, long orderCount,
                                             BigDecimal orderAmount) {
}
