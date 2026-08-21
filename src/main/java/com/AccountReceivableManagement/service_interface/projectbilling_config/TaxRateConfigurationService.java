package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxRateConfigurationResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxRateConfigurationService {

    TaxRateConfigurationResponseDto create(TaxRateConfigurationRequestDto request);

    TaxRateConfigurationResponseDto update(UUID taxRateConfigurationId, TaxRateConfigurationRequestDto request);

    TaxRateConfigurationResponseDto getById(UUID taxRateConfigurationId);

    List<TaxRateConfigurationResponseDto> getAll();

    List<TaxRateConfigurationResponseDto> getActive();

    List<TaxRateConfigurationResponseDto> getByTaxRegion(UUID taxRegionId);

    void deactivate(UUID taxRateConfigurationId);
}
