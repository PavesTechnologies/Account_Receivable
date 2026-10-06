package com.AccountReceivableManagement.service_interface.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanRequestDto;
import com.AccountReceivableManagement.dto.projectbilling_config.BillingMilestonePlanResponseDto;

import java.util.UUID;

public interface BillingMilestonePlanService {

    BillingMilestonePlanResponseDto create(UUID billingConfigurationId, BillingMilestonePlanRequestDto request);

    BillingMilestonePlanResponseDto update(UUID milestonePlanId, BillingMilestonePlanRequestDto request);

    BillingMilestonePlanResponseDto get(UUID milestonePlanId);

    BillingMilestonePlanResponseDto getByBillingConfiguration(UUID billingConfigurationId);

    void delete(UUID milestonePlanId);

}
