package com.AccountReceivableManagement.entity_enums.invoice_generation;

/**
 * The action recorded on one {@code InvoiceApprovalHistory} row - not the
 * resulting status itself (see {@link InvoiceStatus}), the verb that caused
 * the transition. {@code REJECTED} is provisioned now so the history model
 * supports the future rejection flow without a later schema change.
 */
public enum InvoiceApprovalAction {
    SUBMITTED,
    APPROVED,
    REJECTED
}
