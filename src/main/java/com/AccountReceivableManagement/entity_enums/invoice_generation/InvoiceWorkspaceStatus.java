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
    REJECTED,
    /** Approved and its document finalized - not yet sent; delivery state lives on InvoiceDelivery. */
    READY_TO_SEND;

    /**
     * The workspace status of an existing invoice. Exhaustive over
     * {@link InvoiceStatus}: a new invoice status fails compilation here
     * instead of failing the whole workspace at runtime.
     */
    public static InvoiceWorkspaceStatus of(InvoiceStatus status) {
        if (status == null) {
            throw new IllegalStateException("Invoice has no status.");
        }
        return switch (status) {
            case GENERATED -> GENERATED;
            case PENDING_APPROVAL -> PENDING_APPROVAL;
            case APPROVED -> APPROVED;
            case REJECTED -> REJECTED;
            case READY_TO_SEND -> READY_TO_SEND;
        };
    }
}
