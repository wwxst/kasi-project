package com.kasi.backend.promotion.service;

import com.kasi.backend.promotion.enums.FilingMethod;
import com.kasi.backend.promotion.enums.MediaType;

import java.util.Set;

public interface MediaFilingMethodService {
    FilingMethod resolveMethod(String apiFilingMediaTypes, MediaType mediaType);

    void switchMethods(Long connectionId, Set<MediaType> toApi, Set<MediaType> toManual);
}
