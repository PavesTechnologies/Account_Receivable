package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * The outcome of one "Send to Client" delivery attempt - deliberately kept
 * separate from {@link InvoiceStatus}, which tracks the internal
 * approval-workflow state only. An invoice's approval status is never
 * changed by a delivery attempt.
 */
public enum InvoiceDeliveryStatus {
    PENDING,
    SENT,
    FAILED
}
