package com.AccountReceivableManagement.entity_enums.automatic_billing;

/**
 * Pipeline steps of an automatic billing run, in execution order (declaration
 * order is significant - a run's completed step only ever moves forward).
 * Each maps onto one existing service operation:
 * <ul>
 *   <li>SOURCE_DATA_ACQUISITION - T&amp;M: BillingSnapshotService.createBillingSnapshot
 *       (acquisition, rates and amounts); occurrence-based types: the
 *       occurrence's own amount, made TAX_PENDING;</li>
 *   <li>TAX_CALCULATION - TaxCalculationService.findOrCalculateTax / ...ForSchedule;</li>
 *   <li>INVOICE_CREATION - InvoiceService.findOrGenerateInvoice / ...ForSchedule;</li>
 *   <li>SUBMIT_FOR_APPROVAL - InvoiceService.submitForApproval.</li>
 * </ul>
 * Billing calculation is not a step of its own: it happens inside source
 * data acquisition (T&amp;M) or is the occurrence amount. Steps after approval
 * (invoice generation, READY_TO_SEND) are added by the phase that builds them.
 */
public enum AutomaticBillingStep {
    SOURCE_DATA_ACQUISITION,
    TAX_CALCULATION,
    INVOICE_CREATION,
    SUBMIT_FOR_APPROVAL;

    public boolean isAfter(AutomaticBillingStep other) {
        return other == null || ordinal() > other.ordinal();
    }
}
