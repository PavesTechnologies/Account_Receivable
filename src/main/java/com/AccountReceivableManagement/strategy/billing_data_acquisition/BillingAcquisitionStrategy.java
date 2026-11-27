package com.AccountReceivableManagement.strategy.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionResultDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingConfigurationResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.entity_enums.billing_data_acquisition.BillingType;

/**
 * Acquires operational billing data for one billing type. The service
 * layer resolves the right implementation by {@link #getSupportedBillingType()}
 * and never branches on billing type itself.
 */
public interface BillingAcquisitionStrategy {

    BillingType getSupportedBillingType();

    BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration, BillingSnapshotCreateRequestDto request);

    /**
     * As {@link #acquire(BillingConfigurationResponseDto, BillingSnapshotCreateRequestDto)}, with explicit
     * treatment of source records already claimed by a Billing Snapshot. A strategy that does not support
     * incremental acquisition refuses a scope that would skip claimed records, rather than silently billing
     * them twice or failing later.
     */
    default BillingAcquisitionResultDto acquire(BillingConfigurationResponseDto configuration,
            BillingSnapshotCreateRequestDto request,
            com.AccountReceivableManagement.dto.billing_data_acquisition.AcquisitionScope scope) {
        if (scope != null && (scope.skipAlreadyClaimed() || scope.owningSnapshotId() != null)) {
            throw new UnsupportedOperationException("Incremental acquisition is not supported for billing type "
                    + getSupportedBillingType() + ".");
        }
        return acquire(configuration, request);
    }
}
