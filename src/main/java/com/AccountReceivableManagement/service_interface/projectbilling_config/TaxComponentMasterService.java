package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxComponentResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxComponentMasterService {

    TaxComponentResponseDto createTaxComponent(TaxComponentRequestDto request);

    TaxComponentResponseDto updateTaxComponent(UUID taxComponentId, TaxComponentRequestDto request);

    TaxComponentResponseDto getTaxComponentById(UUID taxComponentId);

    List<TaxComponentResponseDto> getAllTaxComponents();

    List<TaxComponentResponseDto> getTaxComponentsByRegime(UUID taxRegimeId);

    void deactivateTaxComponent(UUID taxComponentId);
}
