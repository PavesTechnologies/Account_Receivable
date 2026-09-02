package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.TaxConfigurationComponentResponseDto;

import java.util.List;
import java.util.UUID;

public interface TaxConfigurationComponentService {

    TaxConfigurationComponentResponseDto createComponent(
            UUID taxConfigurationId,
            TaxConfigurationComponentRequestDto request
    );

    TaxConfigurationComponentResponseDto updateComponent(
            UUID taxConfigurationComponentId,
            TaxConfigurationComponentRequestDto request
    );

    TaxConfigurationComponentResponseDto getComponentById(
            UUID taxConfigurationComponentId
    );

    List<TaxConfigurationComponentResponseDto> getComponentsByConfiguration(
            UUID taxConfigurationId
    );

    List<TaxConfigurationComponentResponseDto> getAllComponents();

    void deactivateComponent(UUID taxConfigurationComponentId);
}
