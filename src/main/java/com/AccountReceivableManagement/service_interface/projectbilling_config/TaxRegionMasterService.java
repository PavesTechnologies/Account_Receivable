package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegionResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxRegionMasterService {

    TaxRegionResponseDto createTaxRegion(TaxRegionRequestDto request);

    TaxRegionResponseDto updateTaxRegion(UUID taxRegionId,
                                         TaxRegionRequestDto request);

    TaxRegionResponseDto getTaxRegionById(UUID taxRegionId);

    List<TaxRegionResponseDto> getAllTaxRegions();

    List<TaxRegionResponseDto> getActiveTaxRegions();

    void deleteTaxRegion(UUID taxRegionId);
}
