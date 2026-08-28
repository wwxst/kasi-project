package com.kasi.backend.provider.spi;

import java.util.List;

public interface FreeContentProviderAdapter extends ProviderAdapter {
    List<FreeContentResult> fetchFreeContent(ProviderConnectionSecret connection, String externalDramaId);
}
