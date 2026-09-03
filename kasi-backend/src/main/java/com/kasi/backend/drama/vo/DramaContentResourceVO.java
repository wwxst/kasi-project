package com.kasi.backend.drama.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DramaContentResourceVO {
    private Long id;
    private int sequenceNo;
    private String title;
    private boolean free;
    private String playUrl;
    private String downloadUrl;
}
