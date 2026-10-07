package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRegimeResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxRegimeMasterService {

    TaxRegimeResponseDto createTaxRegime(TaxRegimeRequestDto request);

    TaxRegimeResponseDto updateTaxRegime(UUID taxRegimeId, TaxRegimeRequestDto request);

    TaxRegimeResponseDto getTaxRegimeById(UUID taxRegimeId);

    List<TaxRegimeResponseDto> getAllTaxRegimes();

    List<TaxRegimeResponseDto> getActiveTaxRegimes();

    void deactivateTaxRegime(UUID taxRegimeId);
}
