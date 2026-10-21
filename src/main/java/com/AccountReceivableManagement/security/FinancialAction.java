package com.AccountReceivableManagement.security;

import java.util.List;

/**
 * Financial decisions that require an authorized person. Each maps to a
 * configurable list of JWT authorities ({@code ar.security.authorization.*}).
 */
public enum FinancialAction {

    /** Approve or reject a billing configuration (incl. renewals). */
    BILLING_CONFIGURATION_APPROVAL("approve or reject billing configurations"),

    /** Approve or reject an invoice, and finalize its document. */
    INVOICE_APPROVAL("approve or reject invoices"),

    /** Send an approved invoice to the client. */
    INVOICE_DELIVERY("send invoices to clients"),

    /**
     * Operational recovery: re-queue a failed automatic billing run,
     * reconcile a run with its approved invoice, create a sent invoice's
     * missing receivable, resolve an uncertain invoice delivery.
     */
    OPERATIONS_RECOVERY("perform operational recovery of billing runs, deliveries and receivables");

    private final String description;

    FinancialAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    List<String> authorities(FinancialSecurityProperties.Authorization authorization) {
        return switch (this) {
            case BILLING_CONFIGURATION_APPROVAL -> authorization.getConfigurationApprovalAuthorities();
            case INVOICE_APPROVAL -> authorization.getInvoiceApprovalAuthorities();
            case INVOICE_DELIVERY -> authorization.getInvoiceDeliveryAuthorities();
            case OPERATIONS_RECOVERY -> authorization.getOperationsRecoveryAuthorities();
        };
    }
}
