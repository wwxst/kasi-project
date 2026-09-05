package com.kasi.backend.promotion.service;

public interface MediaFilingTaskService {
    void processDueBatch();

    /**
     * Submit one newly queued filing immediately after its owning transaction commits.
     */
    void submitNow(Long filingId);
}
