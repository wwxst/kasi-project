package com.kasi.backend.user.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserPageVO {
    private List<UserListItemVO> list;
    private int page;
    private int size;
    private long total;
}
