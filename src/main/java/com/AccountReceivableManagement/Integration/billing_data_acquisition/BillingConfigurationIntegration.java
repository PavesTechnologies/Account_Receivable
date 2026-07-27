package com.AccountReceivableManagement.Integration.billing_data_acquisition;

import com.AccountReceivableManagement.DTO.billing_data_acquisition.BillingConfigurationResponseDto;

/**
 * Isolates Epic 2 from how an approved Billing Configuration is actually
 * obtained from Epic 1. The service layer depends only on this contract.
 */
public interface BillingConfigurationIntegration {

    /**
     * Retrieves the approved Billing Configuration for the supplied project.
     *
     * @param projectId project identifier
     * @return the approved Billing Configuration
     * @throws BillingConfigurationNotFoundException if no approved
     *         configuration exists for the project (to be introduced when
     *         this method is backed by a real call to Epic 1)
     */
    BillingConfigurationResponseDto getApprovedBillingConfiguration(Long projectId);
}
