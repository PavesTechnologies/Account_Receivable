package com.AccountReceivableManagement.integration.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;

import java.util.UUID;

/**
 * Isolates Epic 2 from how an approved Billing Configuration is actually
 * obtained from Epic 1.
 */
public interface BillingConfigurationIntegration {

    BillingConfigurationResponseDto getApprovedBillingConfiguration(Long projectId);

    BillingConfigurationResponseDto getApprovedBillingConfigurationById(UUID billingConfigurationId);
}
