package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.AcquireDataResponseDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingAcquisitionRequestDto;

import java.time.LocalDate;
import java.util.UUID;

public interface BillingAcquisitionService {

    /**
     * Creates or updates a manual billing acquisition record for a given configuration
     * and billing period.
     */
    AcquireDataResponseDto createManualAcquisition(UUID billingConfigurationId, LocalDate startDate, LocalDate endDate);

    /**
     * Overload accepting request DTO.
     */
    AcquireDataResponseDto createManualAcquisition(BillingAcquisitionRequestDto requestDto);

    /**
     * Creates or updates a manual billing acquisition record, setting the given
     * snapshot reference and status (READY when {@code status} is blank).
     */
    AcquireDataResponseDto createManualAcquisition(UUID billingConfigurationId, LocalDate startDate,
            LocalDate endDate, UUID snapshotId, String status);

    /**
     * Ensures an acquisition record exists for an already-persisted Billing
     * Snapshot. An existing record for the configuration and period keeps its
     * status and is only re-pointed at {@code snapshotId}; a missing one is
     * created as READY. Used to reconcile snapshots whose acquisition record
     * was never written.
     */
    AcquireDataResponseDto recordAcquisitionForSnapshot(UUID billingConfigurationId, LocalDate startDate,
            LocalDate endDate, UUID snapshotId);
}
