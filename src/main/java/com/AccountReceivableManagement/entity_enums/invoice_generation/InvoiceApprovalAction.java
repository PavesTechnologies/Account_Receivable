package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * The action recorded on one {@code InvoiceApprovalHistory} row - not the
 * resulting status itself (see {@link InvoiceStatus}), the verb that caused
 * the transition. {@code CORRECTED} records a {@code REJECTED -> REJECTED}
 * refresh of a rejected invoice's financial snapshot from corrected
 * upstream BillingSnapshot/TaxCalculation data - it does not change
 * {@code Invoice.status} itself. Column is {@code VARCHAR(20)}; "CORRECTED"
 * (9 chars) fits without a schema change.
 */
public enum InvoiceApprovalAction {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CORRECTED
}
