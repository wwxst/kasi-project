package com.kasi.backend.provider.spi;

/** Provider-neutral page request. updateTime is the remote incremental watermark. */
public record DramaCatalogFetchRequest(int pageNo, int pageSize, String language, Long updateTime) {
    public DramaCatalogFetchRequest(int pageNo, int pageSize, String language) {
        this(pageNo, pageSize, language, null);
    }
}
