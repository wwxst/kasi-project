package com.kasi.backend.provider.service;

import com.kasi.backend.provider.dto.UpsertProviderConnectionDTO;
import com.kasi.backend.provider.vo.ProviderConnectionVO;
import com.kasi.backend.provider.vo.ProviderConnectionTestVO;
import com.kasi.backend.provider.vo.ProviderVO;
import com.kasi.backend.provider.dto.UpdateProviderFilingModeDTO;
import com.kasi.backend.provider.vo.ProviderFilingModeVO;

import java.util.List;

public interface ProviderConnectionService {

    List<ProviderVO> getProviders();

    ProviderConnectionVO upsert(Long operatorId, Long providerId, UpsertProviderConnectionDTO request);

    ProviderConnectionTestVO testConnection(Long providerId);

    ProviderFilingModeVO getFilingMode(Long providerId);

    ProviderFilingModeVO updateFilingMode(Long operatorId, Long providerId, UpdateProviderFilingModeDTO request);
}
