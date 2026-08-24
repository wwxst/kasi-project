package com.kasi.backend.drama.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DramaPageVO {
    private List<DramaListItemVO> list;
    private int page;
    private int size;
    private long total;
}
