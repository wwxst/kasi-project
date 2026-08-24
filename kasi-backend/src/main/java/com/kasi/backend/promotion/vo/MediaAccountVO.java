package com.kasi.backend.promotion.vo;

import com.kasi.backend.promotion.enums.MediaType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MediaAccountVO {
    private Long id;
    private MediaType mediaType;
    private String externalAccountId;
    private String accountName;
    private String accountLink;
    private Integer status;
    private List<MediaFilingVO> filings;
}
