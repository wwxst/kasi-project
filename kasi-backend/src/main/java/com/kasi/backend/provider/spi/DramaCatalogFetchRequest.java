package com.kasi.backend.provider.spi;

/** Provider-neutral page request. updateTime values are converted to GoodShort utimeStart/utimeEnd. */
public record DramaCatalogFetchRequest(int pageNo, int pageSize, String language,
                                       Long updateTime, Long updateTimeEnd) {
    public DramaCatalogFetchRequest(int pageNo, int pageSize, String language) {
        this(pageNo, pageSize, language, null, null);
    }

    public DramaCatalogFetchRequest(int pageNo, int pageSize, String language, Long updateTime) {
        this(pageNo, pageSize, language, updateTime, null);
    }
}
