package com.kasi.backend.promotion.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminMediaAccountDetailVO {
    private Long id;
    private String userNo;
    private String nickname;
    private String realName;
    private MediaAccountDetailVO mediaAccount;
}
