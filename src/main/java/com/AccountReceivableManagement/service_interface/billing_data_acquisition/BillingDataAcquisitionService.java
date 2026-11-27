package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionSummaryDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingDataAcquisitionResponseDto;
import com.AccountReceivableManagement.dto.centralizeddto.PagedResponse;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;

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

    /**
     * One page of the Billing Data Acquisition listing - approved, active
     * configurations ordered by project start date, newest first.
     *
     * @param billingType when non-null, only configurations of that billing type;
     *                    null returns every billing type
     * @param page        zero-based page index
     * @param size        rows per page
     */
    PagedResponse<BillingAcquisitionSummaryDto> getAcquisitionOverview(
            BillingType billingType, int page, int size);
}
