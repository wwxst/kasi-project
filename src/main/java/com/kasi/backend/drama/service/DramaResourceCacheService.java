package com.kasi.backend.drama.service;

import com.kasi.backend.provider.spi.FreeContentResult;

import java.util.List;
import java.util.function.Supplier;

public interface DramaResourceCacheService {
    List<FreeContentResult> get(Long dramaId, Supplier<List<FreeContentResult>> loader);
    void evict(Long dramaId);
}
