package com.kasi.backend.provider.spi;

import com.kasi.backend.promotion.enums.MediaType;

import java.util.Set;

public interface AccountFilingProviderAdapter extends ProviderAdapter {
    Set<MediaType> supportedMediaTypes();
    void submitAccountFiling(ProviderConnectionSecret secret, AccountFilingSubmission submission);
    AccountFilingResult queryAccountFiling(ProviderConnectionSecret secret, AccountFilingQuery query);
}
