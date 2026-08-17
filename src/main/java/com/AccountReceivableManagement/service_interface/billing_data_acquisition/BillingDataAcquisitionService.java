package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;

import java.util.List;

/**
 * Service contract for the Billing Data Acquisition feature.
 * Phase 1: read-only view of ACTIVE billing configurations.
 */
public interface BillingDataAcquisitionService {

    /**
     * Returns all ACTIVE billing configurations formatted for the
     * Billing Data Acquisition overview table, sorted by project name.
     */
    List<BillingDataAcquisitionResponseDto> getActiveConfigurations();
}
