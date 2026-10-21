package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * The outcome of one "Send to Client" delivery attempt - deliberately kept
 * separate from {@link InvoiceStatus}, which tracks the internal
 * approval-workflow state only. An invoice's approval status is never
 * changed by a delivery attempt.
 */
public enum InvoiceDeliveryStatus {
    /**
     * Delivery intent committed; the email is being sent - or, if the row
     * stays PENDING (process stopped, outcome could not be recorded), its
     * outcome is unknown. Blocks another ordinary send until resolved.
     */
    PENDING,
    SENT,
    /** Definitely not delivered; a new ordinary send is allowed. */
    FAILED,
    /**
     * The mail server may have accepted the email (e.g. timeout after the
     * message was transmitted). Never re-sent automatically; an authorized
     * operator resolves it to SENT or FAILED after checking the mailbox.
     */
    UNCERTAIN
}
