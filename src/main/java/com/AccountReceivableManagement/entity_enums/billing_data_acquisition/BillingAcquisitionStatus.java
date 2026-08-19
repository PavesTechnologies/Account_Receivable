package com.AccountReceivableManagement.entity_enums.billing_data_acquisition;

/**
 * Refined Status enum for Billing Acquisition execution records.
 *
 * Lifecycle:
 * - NOT_ACQUIRED: Configuration active, but no acquisition executed for the current period (default).
 * - READY: Acquisition completed, all validations passed.
 * - PARTIALLY_READY: Acquisition completed, but validation/approval issues exist.
 * - ALREADY_BILLED: Invoice finalized for the billing period.
 */
public enum BillingAcquisitionStatus {
    NOT_ACQUIRED,
    READY,
    PARTIALLY_READY,
    ALREADY_BILLED
}
