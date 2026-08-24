package com.kasi.backend.provider.spi;

public interface DramaCatalogProviderAdapter extends ProviderAdapter {

    DramaCatalogPage fetchFullDramas(ProviderConnectionSecret connection, DramaCatalogFetchRequest request);

    DramaCatalogPage fetchIncrementalDramas(ProviderConnectionSecret connection, DramaCatalogFetchRequest request);
}
