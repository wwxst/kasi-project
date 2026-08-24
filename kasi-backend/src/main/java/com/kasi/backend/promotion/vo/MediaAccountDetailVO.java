package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.MediaType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MediaAccountDetailVO {
    private Long id;
    private MediaType mediaType;
    private String externalAccountId;
    private String accountName;
    private String accountLink;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MediaFilingVO> filings;
}
