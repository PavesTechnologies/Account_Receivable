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

    /**
     * Incremental acquisition: creates a NEW acquisition batch (Billing Snapshot plus its acquisition
     * record) for the request's billing configuration and period from only those approved, billable
     * timesheets and eligible expenses that no live (non-cancelled) snapshot claims yet - however many
     * batches, snapshots or invoices the period already has. Existing snapshots and invoices are never
     * read for reuse, changed or re-priced. Fails, creating nothing, when there is no new record (no empty
     * snapshot) or when any new record fails validation (rate, approval, billability).
     *
     * @param idempotencyKey optional; a repeated request with the same configuration and key returns the
     *                       batch the first one created instead of acquiring again
     */
    ApiResponse<BillingSnapshotResponseDto> createIncrementalBatch(BillingSnapshotCreateRequestDto request,
            String idempotencyKey);

    ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd);

    /**
     * As above, but a snapshot that belongs to a different Billing Configuration
     * than {@code billingConfigurationId} is reported as not found. A null
     * {@code billingConfigurationId} behaves exactly like the three-argument form.
     */
    ApiResponse<BillingSnapshotResponseDto> getByProjectAndPeriod(Long projectId, LocalDate billingPeriodStart, LocalDate billingPeriodEnd, UUID billingConfigurationId);

    /**
     * Read-only: every snapshot recorded against {@code billingConfigurationId},
     * latest billing period first, whether that configuration is active,
     * deactivated or expired. Only snapshots whose own
     * {@code billingConfigurationId} matches are returned - never other
     * snapshots of the same project. Empty when there are none.
     */
    ApiResponse<java.util.List<BillingSnapshotResponseDto>> getByBillingConfiguration(UUID billingConfigurationId);

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

    /**
     * Cancels an un-invoiced Billing Snapshot and releases its claimed source
     * records (clearing billedSourceReferenceId on items). If an invoice
     * has already been generated from this snapshot, cancellation is prohibited.
     * Repeated cancellation of an already cancelled snapshot is idempotent.
     */
    ApiResponse<Void> cancelBillingSnapshot(UUID billingSnapshotId);

    /**
     * Same rebuild as {@link #rebuildBillingSnapshot(UUID)}, additionally returning a bounded summary of the
     * repricing (line counts, previous and revised totals) so the correction workflow can record it durably in
     * the invoice approval history.
     */
    com.AccountReceivableManagement.dto.billing_data_acquisition.SnapshotRebuildResult rebuildBillingSnapshotWithAudit(
            UUID billingSnapshotId);
}
