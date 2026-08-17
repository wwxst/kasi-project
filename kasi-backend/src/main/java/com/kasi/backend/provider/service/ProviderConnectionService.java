package com.kasi.backend.provider.service;

import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderVO;

import java.util.List;

public interface ProviderConnectionService {

    List<ProviderVO> getProviders();

    ProviderConnectionVO upsert(Long operatorId, Long providerId, UpsertProviderConnectionDTO request);
}
