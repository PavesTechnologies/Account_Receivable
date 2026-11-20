package com.AccountReceivableManagement.security;

import java.util.List;

/**
 * Financial decisions that require an authorized person.
 *
 * <p>Each action is bound to the AR roles that may take it, in code, next to
 * {@link ArRoles} and the {@code @ManagerOnly} / {@code @ExecutiveOnly}
 * annotations - so a working deployment needs no authorization configuration.
 *
 * <p>{@code ar.security.authorization.*} remains, but only as an
 * <em>override</em>: when a list is set it replaces the default roles for that
 * action (useful to grant a UMS permission, or a different role, per
 * environment). When it is empty the defaults below apply.
 */
public enum FinancialAction {

    /** Approve or reject a billing configuration (incl. renewals). */
    BILLING_CONFIGURATION_APPROVAL("approve or reject billing configurations", ArRoles.FINANCE_MANAGER),

    /** Approve or reject an invoice, and finalize its document. */
    INVOICE_APPROVAL("approve or reject invoices", ArRoles.FINANCE_MANAGER),

    /**
     * Send an approved invoice to the client. Either finance role may send:
     * the API contract assigns this to a "Sender" who is neither AR role
     * specifically (see docs/RBAC_ACCESS_MATRIX.md), so it is not the
     * manager's alone as approval is.
     */
    INVOICE_DELIVERY("send invoices to clients", ArRoles.FINANCE_MANAGER, ArRoles.FINANCE_EXECUTIVE),

    /**
     * Operational recovery: re-queue a failed automatic billing run,
     * reconcile a run with its approved invoice, create a sent invoice's
     * missing receivable, resolve an uncertain invoice delivery.
     */
    OPERATIONS_RECOVERY("perform operational recovery of billing runs, deliveries and receivables",
            ArRoles.FINANCE_MANAGER);

    private final String description;
    private final List<String> defaultRoles;

    FinancialAction(String description, String... defaultRoles) {
        this.description = description;
        this.defaultRoles = List.of(defaultRoles);
    }

    public String description() {
        return description;
    }

    /**
     * The roles that may take this action when nothing is configured, as JWT
     * role authorities ({@code ROLE_<role>}).
     */
    List<String> defaultAuthorities() {
        return defaultRoles.stream().map(RoleNames::roleAuthority).toList();
    }

    /** The configured override for this action, empty when none is set. */
    List<String> authorities(FinancialSecurityProperties.Authorization authorization) {
        return switch (this) {
            case BILLING_CONFIGURATION_APPROVAL -> authorization.getConfigurationApprovalAuthorities();
            case INVOICE_APPROVAL -> authorization.getInvoiceApprovalAuthorities();
            case INVOICE_DELIVERY -> authorization.getInvoiceDeliveryAuthorities();
            case OPERATIONS_RECOVERY -> authorization.getOperationsRecoveryAuthorities();
        };
    }
}
