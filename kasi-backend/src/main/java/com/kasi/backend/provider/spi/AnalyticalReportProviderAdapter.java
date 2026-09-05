package com.kasi.backend.provider.spi;

public interface AnalyticalReportProviderAdapter extends ProviderAdapter {
    ProviderAnalyticalReportPage fetchAnalyticalReports(ProviderConnectionSecret connection,
                                                        AnalyticalReportRequest request);
}
