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
    FAILED,

    /**
     * Only ever written under the {@code local-demo} profile: the delivery ran
     * end to end but NO email left the machine (see
     * {@link com.AccountReceivableManagement.service_interface.email.EmailService#isSimulated()}).
     * Never produced by a real provider, so it cannot be mistaken for {@link #SENT}.
     */
    SIMULATED
}
