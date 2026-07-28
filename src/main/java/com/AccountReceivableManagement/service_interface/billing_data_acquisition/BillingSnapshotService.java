package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;

/**
 * Orchestrates Billing Data Acquisition for Story 2.1: reads the approved
 * Billing Configuration, resolves the client, acquires and validates
 * operational data via the appropriate {@code BillingAcquisitionStrategy},
 * builds and persists the resulting {@code BillingSnapshot}.
 */
public interface BillingSnapshotService {

    ApiResponse<BillingSnapshotResponseDto> createBillingSnapshot(BillingSnapshotCreateRequestDto request);
}
