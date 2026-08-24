package com.kasi.backend.promotion.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MediaAccountStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;
}
