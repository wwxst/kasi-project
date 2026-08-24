package com.kasi.backend.provider.spi;

public interface OrderSyncProviderAdapter extends ProviderAdapter {
    ProviderOrderPage fetchOrders(ProviderConnectionSecret connection, OrderSyncRequest request);
}
