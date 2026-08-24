package com.kasi.backend.provider.vo;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ProviderConnectionTestVO {

    private boolean reachable;
    private String message;
    private Instant testedAt;
}
