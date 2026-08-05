package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ClientResponseDto;
import com.AccountReceivableManagement.dto.projectbilling_config.ProjectResponseDto;

import java.util.List;
import java.util.UUID;

public interface BillingConfigurationService {
    BillingConfigurationResponseDto create(BillingConfigurationRequestDto requestDto);

    BillingConfigurationResponseDto getApprovedByProjectId(Long projectId);

    BillingConfigurationResponseDto approve(UUID billingConfigurationId);

    List<ClientResponseDto> getClients();

    List<ProjectResponseDto> getProjects(UUID clientId);

    BillingConfigurationResponseDto getBillingConfiguration(UUID id);

    List<BillingConfigurationResponseDto> getAllBillingConfigurations();


//    BillingConfigurationResponseDto updateBillingConfiguration(
//            UUID billingConfigurationId,
//            BillingConfigurationRequestDto request);
}
