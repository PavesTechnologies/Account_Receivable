package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxConfigurationService {

    TaxConfigurationResponseDto create(
            TaxConfigurationRequestDto request
    );

    TaxConfigurationResponseDto update(
            UUID taxConfigurationId,
            TaxConfigurationRequestDto request
    );

    TaxConfigurationResponseDto getById(
            UUID taxConfigurationId
    );

    List<TaxConfigurationResponseDto> getAll();

    List<TaxConfigurationResponseDto> getActive();

    List<TaxConfigurationResponseDto> getByTaxRegion(
            UUID taxRegionId
    );

    void deactivate(UUID taxConfigurationId);
}
