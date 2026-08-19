package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.MediaType;

public record AccountFilingQuery(MediaType mediaType, String externalAccountId) {
}
