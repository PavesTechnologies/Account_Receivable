package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingPeriodDiscoveryResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.IncrementalBatchRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.NextBillingPeriodResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;

import java.util.UUID;

/**
 * Incremental, period-wise Time &amp; Material billing: discovers which billing periods of a configuration
 * have newly billable source records, and acquires them as independent batches.
 */
public interface BillingPeriodAcquisitionService {

    /**
     * Every billing period of the configuration's schedule up to the period containing today - including
     * periods already acquired or invoiced - with its newly eligible (approved, billable, unclaimed)
     * timesheets and expenses, rate validation, acquisition readiness and existing batches/invoices.
     * Read-only: nothing is claimed or persisted.
     *
     * @param onlyWithNewItems return only periods that have newly eligible records
     */
    BillingPeriodDiscoveryResponseDto discoverPeriods(UUID billingConfigurationId, boolean onlyWithNewItems);

    /**
     * The one billing period to acquire next, derived from {@link #discoverPeriods} (same configuration
     * lookup and validation): the earliest started period with newly eligible records, otherwise the most
     * recent started period. Read-only.
     */
    NextBillingPeriodResponseDto nextPeriod(UUID billingConfigurationId);

    /**
     * Acquires the newly eligible records of exactly one of the configuration's billing periods as a new
     * batch (see {@link BillingSnapshotService#createIncrementalBatch}).
     */
    ApiResponse<BillingSnapshotResponseDto> acquireBatch(UUID billingConfigurationId, IncrementalBatchRequestDto request);
}
