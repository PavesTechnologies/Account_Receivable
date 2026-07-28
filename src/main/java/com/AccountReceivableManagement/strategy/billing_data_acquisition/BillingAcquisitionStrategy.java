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
}
