package com.AccountReceivableManagement.service_interface.billing_data_acquisition;

import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotCreateRequestDto;
import com.AccountReceivableManagement.dto.billing_data_acquisition.BillingSnapshotResponseDto;
import com.AccountReceivableManagement.dto.common.ApiResponse;
import com.AccountReceivableManagement.entity.billing_data_acquisition.BillingSnapshot;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrates Billing Data Acquisition for Story 2.1: reads the approved
 * Billing Configuration, resolves the client, acquires and validates
 * operational data via the appropriate {@code BillingAcquisitionStrategy},
 * builds and persists the resulting {@code BillingSnapshot}.
 */
public interface BillingSnapshotService {

    ApiResponse<BillingSnapshotResponseDto> createBillingSnapshot(BillingSnapshotCreateRequestDto request);

    ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    /**
     * Phase 2B financial correction - rebuilds an already-persisted
     * {@code BillingSnapshot} in place from freshly re-acquired authoritative
     * source data (TMS, for Time &amp; Material), rather than creating a new
     * snapshot. Not reachable through the normal acquisition flow
     * ({@link #createBillingSnapshot(BillingSnapshotCreateRequestDto)} is
     * untouched and keeps its existing "return existing snapshot" behavior);
     * intended only for orchestration by a correction-specific caller that
     * has already verified its owning Invoice is {@code REJECTED}.
     * Re-resolves the snapshot's own {@code billingConfigurationId} and
     * billing period (never searches by project+period), re-runs the
     * billing-type-resolved {@code BillingAcquisitionStrategy} and the same
     * {@code BillingAcquisitionValidator}, then replaces only
     * {@code items}, {@code subtotal}, {@code expenseAmount},
     * {@code totalAmount}, and resets {@code status} to
     * {@code READY_FOR_TAX} so the existing, unmodified
     * {@code TaxCalculationService.calculateTax(UUID)} can run again.
     * {@code billingConfigurationId}, {@code clientId}, {@code projectId},
     * billing period, currency, payment term, billing frequency, and tax
     * region/jurisdiction fields are left untouched. Throws the existing
     * {@code ResourceNotFoundException}/{@code ValidationException}
     * mechanism on failure and leaves the snapshot completely unmodified if
     * acquisition validation fails.
     */
    BillingSnapshot rebuildBillingSnapshot(UUID billingSnapshotId);
}
