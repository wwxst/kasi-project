package com.kasi.backend.provider.goodshort;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "app.providers.goodshort")
public class GoodShortProperties {

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration readTimeout;
}
