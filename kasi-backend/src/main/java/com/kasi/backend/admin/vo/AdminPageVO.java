package com.kasi.backend.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminPageVO {

    private List<AdminListItemVO> list;
    private int page;
    private int size;
    private long total;
}
