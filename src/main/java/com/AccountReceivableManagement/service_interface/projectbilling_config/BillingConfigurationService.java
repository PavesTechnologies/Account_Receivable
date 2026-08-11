package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.*;

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

    BillingConfigurationResponseDto updateBillingConfiguration(
            UUID billingConfigurationId,
            BillingConfigurationRequestDto request);

    void deactivateBillingConfiguration(
            UUID billingConfigurationId);

    BillingConfigurationResponseDto reject(UUID billingConfigurationId, BillingConfigurationRejectRequestDto request);

    BillingConfigurationResponseDto activate(UUID billingConfigurationId);
}
