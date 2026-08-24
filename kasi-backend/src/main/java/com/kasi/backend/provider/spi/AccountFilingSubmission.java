package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.MediaType;

public record AccountFilingSubmission(
        MediaType mediaType,
        String externalAccountId,
        String accountName,
        String accountLink) {
}
