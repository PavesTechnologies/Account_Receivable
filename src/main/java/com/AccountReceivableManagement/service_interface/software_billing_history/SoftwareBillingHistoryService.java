package com.AccountReceivableManagement.service_interface.software_billing_history;

import com.AccountReceivableManagement.dto.software_billing_history.SoftwareBillingHistoryResponseDto;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;

import java.util.List;
import java.util.UUID;

public interface SoftwareBillingHistoryService {

    /**
     * Persists one {@code SoftwareBillingHistory} row for every SOFTWARE item
     * on the given, already-persisted snapshot.
     *
     * @throws com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler.ValidationException
     *         if any of those assets was already billed for this exact billing period
     */
    void recordHistory(BillingSnapshot snapshot);

    List<SoftwareBillingHistoryResponseDto> getHistoryForAsset(UUID assetId);
}
