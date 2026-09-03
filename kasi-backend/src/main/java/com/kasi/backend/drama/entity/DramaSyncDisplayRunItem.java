package com.kasi.backend.drama.entity;

import com.kasi.backend.drama.enums.DramaSyncDomain;
import lombok.Data;

@Data
public class DramaSyncDisplayRunItem {
    private String runId;
    private DramaSyncDomain taskDomain;
    private Long taskId;
}
