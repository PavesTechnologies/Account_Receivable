package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * Response-only status of a row in the Invoice Generation workspace. Never
 * persisted: {@code READY_FOR_INVOICE} is derived from a
 * {@code TAX_COMPLETED} BillingSnapshot that has no Invoice yet; every other
 * value mirrors the persisted {@link InvoiceStatus} of the existing invoice.
 */
public enum InvoiceWorkspaceStatus {
    READY_FOR_INVOICE,
    GENERATED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
